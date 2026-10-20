// FAMessage web: misma funcionalidad y misma base de datos que la app Android.
// Firestore:
//   usuarios/{uid}                    { email, nombre, foto, rol }
//   chats/{uidA_uidB}                 { participantes, ultimoMensaje, ultimoTimestamp, ultimoEmisor, ultimoLeido }
//   chats/{uidA_uidB}/mensajes/{id}   { texto, emisorId, timestamp, leido }
//   llamadas/{uidQueRecibe}           { de, deNombre, sala (= callId), estado, timestamp }
//   videollamadas/{callId}            { de, para, timestamp, offer, answer, colgada }
//   videollamadas/{callId}/candidatos_de|candidatos_para/{id}   { candidate, sdpMid, sdpMLineIndex }

import { initializeApp } from "https://www.gstatic.com/firebasejs/10.12.5/firebase-app.js";
import {
  getAuth, onAuthStateChanged, signInWithEmailAndPassword, createUserWithEmailAndPassword,
  sendPasswordResetEmail, sendEmailVerification, signOut
} from "https://www.gstatic.com/firebasejs/10.12.5/firebase-auth.js";
import {
  getFirestore, doc, getDoc, setDoc, updateDoc, collection, addDoc, query, where, orderBy,
  onSnapshot, writeBatch
} from "https://www.gstatic.com/firebasejs/10.12.5/firebase-firestore.js";

import { firebaseConfig, ICE_SERVERS } from "./firebase-config.js";
import { t, idioma, alternarIdioma, traducirPagina } from "./i18n.js";

const app = initializeApp(firebaseConfig);
const auth = getAuth(app);
const db = getFirestore(app);

// Videollamada: mismos valores que Videollamada.kt en Android
const LLAMADA = { SONANDO: "sonando", ACEPTADA: "aceptada", RECHAZADA: "rechazada", PERDIDA: "perdida" };
const SEGUNDOS_TIMBRE = 40;
const SEGUNDOS_SIN_RESPUESTA = 45;   // quien llama se rinde si el otro no contesta
const SEGUNDOS_PARA_CONECTAR = 30;   // con oferta y respuesta, margen para que los dos se encuentren
const SEGUNDOS_DESCONECTADO = 10;    // corte de red tolerado antes de colgar
const COLORES_AVATAR = ["#EC3B5E", "#FF6B4A", "#7C4DFF", "#00A68C", "#2F80ED", "#F2994A"];

const $ = (sel, raiz = document) => raiz.querySelector(sel);

const estado = {
  usuario: null,         // usuario de Firebase Auth
  perfil: null,          // mi documento en usuarios/{uid}
  vista: null,
  limpiezaVista: [],     // listeners de la pantalla actual
  limpiezaSesion: [],    // listeners que viven mientras hay sesión
  arrancado: false
};

/* =====================================================================
   Utilidades
   ===================================================================== */

function chatIdEntre(a, b) {
  return a < b ? `${a}_${b}` : `${b}_${a}`;
}

function nombreVisible(u) {
  const nombre = (u?.nombre || "").trim();
  if (nombre) return nombre;
  const deCorreo = (u?.email || "").split("@")[0];
  return deCorreo || "Usuario";
}

function colorPara(texto) {
  let h = 0;
  for (const c of texto) h = (h * 31 + c.charCodeAt(0)) | 0;
  return COLORES_AVATAR[((h % COLORES_AVATAR.length) + COLORES_AVATAR.length) % COLORES_AVATAR.length];
}

// Foto si existe; si no, la inicial sobre un color propio del contacto
function pintarAvatar(el, nombre, foto, { colorear = true } = {}) {
  if (foto) {
    el.textContent = "";
    el.style.backgroundImage = `url("data:image/jpeg;base64,${foto}")`;
  } else {
    el.style.backgroundImage = "";
    el.textContent = (nombre || "U").charAt(0).toUpperCase();
  }
  el.style.backgroundColor = colorear && !foto ? colorPara(nombre || "") : "";
}

let temporizadorToast = null;
function toast(mensaje, ms = 2800) {
  const el = $("#toast");
  el.textContent = mensaje;
  el.classList.add("show");
  clearTimeout(temporizadorToast);
  temporizadorToast = setTimeout(() => el.classList.remove("show"), ms);
}

function hora(ts) {
  return new Date(ts).toLocaleTimeString(idioma(), { hour: "2-digit", minute: "2-digit", hour12: false });
}

// Hoy: "14:05" · ayer: "Ayer" · antes: "12/10"
function horaCorta(ts) {
  const fecha = new Date(ts);
  const hoy = new Date();
  const ayer = new Date();
  ayer.setDate(hoy.getDate() - 1);
  if (fecha.toDateString() === hoy.toDateString()) return hora(ts);
  if (fecha.toDateString() === ayer.toDateString()) return t("yesterday");
  return fecha.toLocaleDateString(idioma(), { day: "2-digit", month: "2-digit" });
}

function mensajeDeError(error) {
  switch (error?.code) {
    case "auth/invalid-credential":
    case "auth/invalid-login-credentials":
    case "auth/wrong-password":
    case "auth/user-not-found":
    case "auth/invalid-email":
      return t("error_wrong_credentials");
    case "auth/email-already-in-use":
      return t("error_email_in_use");
    case "auth/weak-password":
      return t("error_password_short");
    case "auth/network-request-failed":
    case "unavailable":
      return t("error_no_internet");
    case "auth/too-many-requests":
      return t("error_too_many_requests");
    default:
      return t("error_generic", error?.message || "");
  }
}

const correoValido = (email) => /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email);

function marcarError(form, nombre, clave) {
  const input = form.elements[nombre];
  const campo = input.closest(".field");
  campo.classList.add("has-error");
  $(".field-error", campo).textContent = t(clave);
  input.focus();
}

function limpiarErrores(form) {
  form.querySelectorAll(".field").forEach((campo) => {
    campo.classList.remove("has-error");
    const error = $(".field-error", campo);
    if (error) error.textContent = "";
  });
}

/* =====================================================================
   Navegación (una "pantalla" por hash: #login, #home, #chat/uid, ...)
   ===================================================================== */

const PUBLICAS = ["login", "signup"];

function ir(ruta) {
  if (location.hash === `#${ruta}`) mostrarRuta();
  else location.hash = ruta;
}

function mostrarRuta() {
  if (!estado.arrancado) return;
  let [vista, parametro] = location.hash.replace(/^#/, "").split("/");
  const conSesion = !!estado.usuario;

  if (!conSesion && !PUBLICAS.includes(vista)) vista = "login";
  if (conSesion && (PUBLICAS.includes(vista) || !vista)) vista = "home";
  if (!["login", "signup", "home", "chats", "chat", "perfil"].includes(vista)) vista = conSesion ? "home" : "login";
  if (vista === "chat" && !parametro) vista = "chats";

  // Si redirigimos (p. ej. de #signup a #home al registrarse), la dirección también cambia;
  // si no, al cerrar sesión volveríamos a la pantalla de registro en vez de a la de inicio
  const destino = vista === "chat" ? `#chat/${parametro}` : `#${vista}`;
  if (location.hash !== destino) history.replaceState(null, "", destino);

  // Al cambiar de pantalla soltamos sus listeners
  estado.limpiezaVista.forEach((fn) => fn());
  estado.limpiezaVista = [];

  document.querySelectorAll(".view").forEach((v) => { v.hidden = true; });
  $(`#view-${vista}`).hidden = false;
  estado.vista = vista;
  window.scrollTo(0, 0);

  const entrar = { login: entrarLogin, signup: entrarSignup, home: entrarHome, chats: entrarChats, chat: entrarChat, perfil: entrarPerfil }[vista];
  entrar?.(parametro);
}

window.addEventListener("hashchange", mostrarRuta);

/* =====================================================================
   Iniciar sesión / Registro / Recuperar contraseña
   ===================================================================== */

function entrarLogin() {
  limpiarErrores($("#form-login"));
}

function entrarSignup() {
  limpiarErrores($("#form-signup"));
}

$("#form-login").addEventListener("submit", async (e) => {
  e.preventDefault();
  const form = e.currentTarget;
  limpiarErrores(form);
  const email = form.email.value.trim();
  const password = form.password.value;

  if (!email) return marcarError(form, "email", "error_required");
  if (!password) return marcarError(form, "password", "error_required");

  const boton = $("button[type=submit]", form);
  boton.disabled = true;
  try {
    const cred = await signInWithEmailAndPassword(auth, email, password);
    // Cuentas que se quedaron sin perfil: lo creamos para que aparezcan en los chats
    try {
      const ref = doc(db, "usuarios", cred.user.uid);
      const perfil = await getDoc(ref);
      if (!perfil.exists()) await setDoc(ref, { email, rol: "usuario" });
    } catch {
      toast(t("error_profile_read"));
    }
    form.reset();
    toast(t("sign_in_success"));
  } catch (error) {
    toast(mensajeDeError(error));
  } finally {
    boton.disabled = false;
  }
});

$("#form-signup").addEventListener("submit", async (e) => {
  e.preventDefault();
  const form = e.currentTarget;
  limpiarErrores(form);
  const nombre = form.nombre.value.trim();
  const email = form.email.value.trim();
  const password = form.password.value;
  const confirm = form.confirm.value;

  if (!nombre) return marcarError(form, "nombre", "error_required");
  if (!email) return marcarError(form, "email", "error_required");
  if (!correoValido(email)) return marcarError(form, "email", "error_email_invalid");
  if (password.length < 6) return marcarError(form, "password", "error_password_short");
  if (password !== confirm) return marcarError(form, "confirm", "error_passwords_mismatch");

  const boton = $("button[type=submit]", form);
  boton.disabled = true;
  try {
    const cred = await createUserWithEmailAndPassword(auth, email, password);
    await setDoc(doc(db, "usuarios", cred.user.uid), { email, nombre, rol: "usuario" });
    auth.languageCode = idioma();   // el correo de Firebase llega en el idioma de la app
    sendEmailVerification(cred.user).catch(() => {});
    form.reset();
    toast(t("sign_up_success"), 4000);
  } catch (error) {
    toast(mensajeDeError(error));
  } finally {
    boton.disabled = false;
  }
});

const dialogoReset = $("#dialog-reset");
const formReset = $("#form-reset");

function abrirRecuperar() {
  limpiarErrores(formReset);
  formReset.email.value = $("#form-login").email.value.trim();
  if (dialogoReset.showModal) dialogoReset.showModal();
  else dialogoReset.setAttribute("open", "");
}

formReset.addEventListener("submit", async (e) => {
  e.preventDefault();
  limpiarErrores(formReset);
  const email = formReset.email.value.trim();
  if (!correoValido(email)) return marcarError(formReset, "email", "error_email_invalid");
  dialogoReset.close();
  try {
    auth.languageCode = idioma();   // el correo de Firebase llega en el idioma de la app
    await sendPasswordResetEmail(auth, email);
    toast(t("reset_sent"), 4000);
  } catch (error) {
    toast(mensajeDeError(error));
  }
});

/* =====================================================================
   Inicio
   ===================================================================== */

function pintarInicio() {
  const perfil = estado.perfil || { email: estado.usuario?.email };
  const nombre = nombreVisible(perfil);
  $("#home-hello").textContent = t("hello_name", nombre);
  $("#home-email").textContent = estado.usuario?.email || "";
  pintarAvatar($("#home-avatar"), nombre, perfil.foto, { colorear: false });
  $("#home-admin").hidden = perfil.rol !== "admin";
}

function entrarHome() {
  pintarInicio();
}

/* =====================================================================
   Lista de conversaciones
   ===================================================================== */

function entrarChats() {
  const uid = estado.usuario.uid;
  let usuarios = [];
  let chats = new Map();
  let cargados = false;

  const lista = $("#chats-list");
  const buscador = $("#chats-search");
  $("#chats-loading").hidden = false;
  $("#chats-empty").hidden = true;
  lista.innerHTML = "";
  buscador.value = "";

  const pintar = () => {
    if (!cargados) return;
    const filtro = buscador.value.trim().toLowerCase();
    const conversaciones = usuarios
      .filter((u) => !filtro || nombreVisible(u).toLowerCase().includes(filtro) || (u.email || "").toLowerCase().includes(filtro))
      .map((u) => {
        const c = chats.get(chatIdEntre(uid, u.uid)) || {};
        return {
          usuario: u,
          ultimoMensaje: c.ultimoMensaje || "",
          ultimoTimestamp: c.ultimoTimestamp || 0,
          ultimoEmisor: c.ultimoEmisor || "",
          ultimoLeido: c.ultimoLeido ?? true
        };
      })
      .sort((a, b) => b.ultimoTimestamp - a.ultimoTimestamp ||
        nombreVisible(a.usuario).localeCompare(nombreVisible(b.usuario)));

    lista.innerHTML = "";
    for (const conv of conversaciones) lista.appendChild(itemConversacion(conv, uid));

    $("#chats-empty").hidden = conversaciones.length > 0;
    $("#chats-empty-text").textContent = t("users_empty");
  };

  buscador.oninput = pintar;

  const quitarUsuarios = onSnapshot(collection(db, "usuarios"), (snap) => {
    cargados = true;
    $("#chats-loading").hidden = true;
    usuarios = snap.docs.filter((d) => d.id !== uid).map((d) => ({ uid: d.id, ...d.data() }));
    pintar();
  }, () => {
    $("#chats-loading").hidden = true;
    if (!cargados) {
      $("#chats-empty").hidden = false;
      $("#chats-empty-text").textContent = t("users_load_error");
    }
  });

  // Si esta consulta falla, la lista sigue funcionando sin el último mensaje
  const quitarChats = onSnapshot(
    query(collection(db, "chats"), where("participantes", "array-contains", uid)),
    (snap) => {
      chats = new Map(snap.docs.map((d) => [d.id, d.data()]));
      pintar();
    },
    () => {}
  );

  estado.limpiezaVista.push(quitarUsuarios, quitarChats);
}

function itemConversacion(conv, miUid) {
  const u = conv.usuario;
  const nombre = nombreVisible(u);
  const yaHablaron = conv.ultimoTimestamp > 0;
  const esMio = conv.ultimoEmisor === miUid;
  const sinLeer = yaHablaron && !esMio && !conv.ultimoLeido;

  const boton = document.createElement("button");
  boton.type = "button";
  boton.className = "card conv" + (sinLeer ? " unread" : "");

  const avatar = document.createElement("span");
  avatar.className = "avatar avatar-md";
  pintarAvatar(avatar, nombre, u.foto);

  const texto = document.createElement("span");
  texto.className = "conv-text";
  const b = document.createElement("b");
  b.textContent = nombre;
  const resumen = document.createElement("span");
  resumen.textContent = !yaHablaron ? t("user_item_hint") : esMio ? t("you_prefix", conv.ultimoMensaje) : conv.ultimoMensaje;
  texto.append(b, resumen);

  const lado = document.createElement("span");
  lado.className = "conv-side";
  if (yaHablaron) {
    const horaEl = document.createElement("span");
    horaEl.className = "conv-time";
    horaEl.textContent = horaCorta(conv.ultimoTimestamp);
    lado.appendChild(horaEl);
    if (sinLeer) {
      const punto = document.createElement("span");
      punto.className = "dot";
      lado.appendChild(punto);
    }
  } else {
    lado.innerHTML = '<span class="icon-soft conv-icon"><svg viewBox="0 0 24 24" aria-hidden="true"><path d="M20 2H4a2 2 0 00-2 2v18l4-4h14a2 2 0 002-2V4a2 2 0 00-2-2zm-3 11H7v-2h10v2zm0-3H7V8h10v2z"/></svg></span>';
  }

  boton.append(avatar, texto, lado);
  boton.addEventListener("click", () => ir(`chat/${u.uid}`));
  return boton;
}

/* =====================================================================
   Chat
   ===================================================================== */

const chat = {
  otroUid: "",
  otroNombre: "",
  chatId: "",
  mensajes: []
};

function entrarChat(otroUid) {
  const miUid = estado.usuario.uid;
  chat.otroUid = otroUid;
  chat.chatId = chatIdEntre(miUid, otroUid);
  chat.mensajes = [];
  chat.otroNombre = "";

  const contenedor = $("#chat-messages");
  contenedor.innerHTML = "";
  $("#chat-name").textContent = "";
  pintarAvatar($("#chat-avatar"), "", "", { colorear: false });

  const quitarOtro = onSnapshot(doc(db, "usuarios", otroUid), (snap) => {
    if (!snap.exists()) return;
    const otro = snap.data();
    chat.otroNombre = nombreVisible(otro);
    $("#chat-name").textContent = chat.otroNombre;
    pintarAvatar($("#chat-avatar"), chat.otroNombre, otro.foto, { colorear: false });
  }, () => {});

  const quitarMensajes = onSnapshot(
    query(collection(db, "chats", chat.chatId, "mensajes"), orderBy("timestamp", "asc")),
    (snap) => {
      chat.mensajes = snap.docs;
      const abajo = contenedor.scrollHeight - contenedor.scrollTop - contenedor.clientHeight < 80;
      contenedor.innerHTML = "";
      for (const d of snap.docs) contenedor.appendChild(burbuja(d.data(), miUid));
      if (abajo || snap.docChanges().some((c) => c.type === "added")) contenedor.scrollTop = contenedor.scrollHeight;
      marcarComoLeidos();
    },
    (error) => toast(mensajeDeError(error))
  );

  const alVolver = () => marcarComoLeidos();
  document.addEventListener("visibilitychange", alVolver);

  estado.limpiezaVista.push(quitarOtro, quitarMensajes, () => document.removeEventListener("visibilitychange", alVolver));
  setTimeout(() => $("#chat-input").focus({ preventScroll: true }), 50);
}

function burbuja(m, miUid) {
  const mio = m.emisorId === miUid;
  const fila = document.createElement("div");
  fila.className = "msg " + (mio ? "mine" : "other");

  const texto = document.createElement("div");
  texto.className = "bubble";
  texto.textContent = m.texto || "";

  const meta = document.createElement("div");
  meta.className = "meta";
  if (m.timestamp) {
    const h = document.createElement("span");
    h.textContent = hora(m.timestamp);
    meta.appendChild(h);
  }
  // Confirmación de lectura: ✓ enviado · ✓✓ azul visto
  if (mio) {
    const check = document.createElement("span");
    check.className = "check" + (m.leido ? " read" : "");
    check.textContent = m.leido ? "✓✓" : "✓";
    if (m.leido) check.title = t("chat_seen");
    meta.appendChild(check);
  }

  fila.append(texto, meta);
  return fila;
}

function marcarComoLeidos() {
  if (estado.vista !== "chat" || document.visibilityState !== "visible") return;
  const sinLeer = chat.mensajes.filter((d) => d.data().emisorId === chat.otroUid && d.data().leido !== true);
  if (!sinLeer.length) return;

  const lote = writeBatch(db);
  sinLeer.forEach((d) => lote.update(d.ref, { leido: true }));
  lote.set(doc(db, "chats", chat.chatId), { ultimoLeido: true }, { merge: true });
  lote.commit().catch(() => {});
}

// La llamada pasa chatId y otroUid propios por si mientras tanto se abrió otro chat
async function guardarMensaje(texto, chatId = chat.chatId, otroUid = chat.otroUid) {
  const miUid = estado.usuario.uid;
  const ahora = Date.now();
  const chatRef = doc(db, "chats", chatId);
  await Promise.all([
    addDoc(collection(chatRef, "mensajes"), { texto, emisorId: miUid, timestamp: ahora, leido: false }),
    // Resumen para la lista de conversaciones
    setDoc(chatRef, {
      participantes: [miUid, otroUid],
      ultimoMensaje: texto,
      ultimoTimestamp: ahora,
      ultimoEmisor: miUid,
      ultimoLeido: false
    }, { merge: true })
  ]);
}

const entradaChat = $("#chat-input");

$("#form-message").addEventListener("submit", (e) => {
  e.preventDefault();
  const texto = entradaChat.value.trim();
  if (!texto) return;
  entradaChat.value = "";
  ajustarAltura();
  guardarMensaje(texto).catch((error) => toast(mensajeDeError(error)));
  entradaChat.focus();
});

// Enter envía; Shift+Enter hace salto de línea (en computadora)
entradaChat.addEventListener("keydown", (e) => {
  if (e.key === "Enter" && !e.shiftKey && !e.isComposing && window.matchMedia("(pointer: fine)").matches) {
    e.preventDefault();
    $("#form-message").requestSubmit();
  }
});

function ajustarAltura() {
  entradaChat.style.height = "auto";
  entradaChat.style.height = Math.min(entradaChat.scrollHeight, 120) + "px";
}
entradaChat.addEventListener("input", ajustarAltura);

/* =====================================================================
   Mi perfil
   ===================================================================== */

let fotoEditada = "";
let fotoTocada = false;

function entrarPerfil() {
  const form = $("#form-perfil");
  limpiarErrores(form);
  form.nombre.value = "";
  form.email.value = estado.usuario.email || "";
  fotoEditada = "";
  fotoTocada = false;
  rellenarPerfil();
}

// El perfil puede llegar de Firebase después de abrir la pantalla: rellenamos
// sin pisar lo que el usuario ya haya escrito o elegido
function rellenarPerfil() {
  const form = $("#form-perfil");
  const perfil = estado.perfil || {};
  if (!form.nombre.value) form.nombre.value = perfil.nombre || "";
  if (!fotoTocada) fotoEditada = perfil.foto || "";
  pintarFotoPerfil();
}

function pintarFotoPerfil() {
  const form = $("#form-perfil");
  const nombre = form.nombre.value.trim() || (estado.usuario.email || "").split("@")[0];
  pintarAvatar($("#perfil-avatar"), nombre, fotoEditada, { colorear: false });
  $("#perfil-remove").hidden = !fotoEditada;
}

$("#form-perfil").nombre.addEventListener("input", pintarFotoPerfil);

// Recorta en cuadrado de 256 px y comprime a JPEG en Base64 (igual que Avatar.kt en Android)
function comprimirFoto(archivo) {
  return new Promise((resolve, reject) => {
    const url = URL.createObjectURL(archivo);
    const img = new Image();
    img.onload = () => {
      const lado = Math.min(img.naturalWidth, img.naturalHeight);
      const canvas = document.createElement("canvas");
      canvas.width = canvas.height = 256;
      canvas.getContext("2d").drawImage(
        img,
        (img.naturalWidth - lado) / 2, (img.naturalHeight - lado) / 2, lado, lado,
        0, 0, 256, 256
      );
      URL.revokeObjectURL(url);
      resolve(canvas.toDataURL("image/jpeg", 0.8).split(",")[1]);
    };
    img.onerror = () => {
      URL.revokeObjectURL(url);
      reject(new Error("imagen"));
    };
    img.src = url;
  });
}

$("#perfil-file").addEventListener("change", async (e) => {
  const archivo = e.target.files?.[0];
  e.target.value = "";
  if (!archivo) return;
  try {
    fotoEditada = await comprimirFoto(archivo);
    fotoTocada = true;
    pintarFotoPerfil();
  } catch {
    toast(t("profile_photo_error"));
  }
});

$("#form-perfil").addEventListener("submit", async (e) => {
  e.preventDefault();
  const form = e.currentTarget;
  limpiarErrores(form);
  const nombre = form.nombre.value.trim();
  if (!nombre) return marcarError(form, "nombre", "error_required");

  const boton = $("button[type=submit]", form);
  boton.disabled = true;
  try {
    await setDoc(doc(db, "usuarios", estado.usuario.uid),
      { nombre, foto: fotoEditada, email: estado.usuario.email || "" }, { merge: true });
    toast(t("profile_saved"));
    ir("home");
  } catch (error) {
    toast(mensajeDeError(error));
  } finally {
    boton.disabled = false;
  }
});

/* =====================================================================
   Timbre (los navegadores solo dejan sonar audio después de un toque)
   ===================================================================== */

const timbre = {
  contexto: null,
  intervalo: null,
  desbloqueado: false
};

function desbloquearAudio() {
  try {
    timbre.contexto = timbre.contexto || new (window.AudioContext || window.webkitAudioContext)();
    if (timbre.contexto.state === "suspended") timbre.contexto.resume();
    // Un sonido silencioso "despierta" el audio en Safari
    const osc = timbre.contexto.createOscillator();
    const vol = timbre.contexto.createGain();
    vol.gain.value = 0;
    osc.connect(vol).connect(timbre.contexto.destination);
    osc.start();
    osc.stop(timbre.contexto.currentTime + 0.05);
    timbre.desbloqueado = true;
    $("#sound-chip").hidden = true;
  } catch { /* sin Web Audio: la llamada se muestra igual, solo sin sonido */ }
}

["pointerdown", "keydown", "touchend"].forEach((evento) =>
  document.addEventListener(evento, desbloquearAudio, { passive: true })
);

function tono(frecuencia, inicio, duracion) {
  const ctx = timbre.contexto;
  const osc = ctx.createOscillator();
  const vol = ctx.createGain();
  osc.type = "sine";
  osc.frequency.value = frecuencia;
  vol.gain.setValueAtTime(0, ctx.currentTime + inicio);
  vol.gain.linearRampToValueAtTime(0.25, ctx.currentTime + inicio + 0.02);
  vol.gain.setValueAtTime(0.25, ctx.currentTime + inicio + duracion - 0.05);
  vol.gain.linearRampToValueAtTime(0, ctx.currentTime + inicio + duracion);
  osc.connect(vol).connect(ctx.destination);
  osc.start(ctx.currentTime + inicio);
  osc.stop(ctx.currentTime + inicio + duracion);
}

function empezarTimbre() {
  detenerTimbre();
  const sonar = () => {
    if (timbre.contexto && timbre.contexto.state === "running") {
      tono(880, 0, 0.35);
      tono(660, 0.4, 0.35);
      tono(880, 0.8, 0.35);
    }
    navigator.vibrate?.([700, 500]);
  };
  sonar();
  timbre.intervalo = setInterval(sonar, 2400);
}

function detenerTimbre() {
  clearInterval(timbre.intervalo);
  timbre.intervalo = null;
  navigator.vibrate?.(0);
}

/* =====================================================================
   Videollamadas (WebRTC propio: el audio y el video van directo entre los
   dos dispositivos; Firestore solo lleva la oferta, la respuesta y los candidatos)
   ===================================================================== */

// 640x480 alcanza para un celular y no satura la red de ninguno de los dos
const MEDIOS = {
  video: { facingMode: "user", width: { ideal: 640 }, height: { ideal: 480 } },
  audio: { echoCancellation: true, noiseSuppression: true }
};

let llamada = null;   // la llamada abierta; mientras exista estamos "ocupados"

function nuevaLlamada(datos) {
  return {
    ...datos,                 // callId, soyQuienLlama, otroUid, otroNombre, timestamp
    pc: null,
    local: null,              // mi cámara y micrófono
    remoto: null,             // solo si el otro manda pistas sin stream
    emisorVideo: null,        // para cambiar de cámara sin renegociar
    cola: [],                 // candidatos que llegan antes que la descripción remota
    remotaLista: false,
    respuestaRecibida: false,
    documentoCreado: false,   // ya existe videollamadas/{callId}: al colgar lo marcamos
    hayVideoRemoto: false,
    inicio: 0,                // cuándo se conectó (para el cronómetro)
    quitar: [],               // listeners de Firestore
    temporizadorRespuesta: null,
    temporizadorConectar: null,
    temporizadorCorte: null,
    reloj: null,
    cambiandoCamara: false,
    terminada: false
  };
}

async function pedirMedios() {
  // Sin HTTPS (o en navegadores muy viejos) no hay acceso a la cámara; y sin WebRTC
  // (desactivado en Firefox, Tor) no tiene sentido pedirla
  if (!navigator.mediaDevices?.getUserMedia || typeof RTCPeerConnection === "undefined") {
    toast(t("call_failed"));
    return null;
  }
  try {
    return await navigator.mediaDevices.getUserMedia(MEDIOS);
  } catch (error) {
    // Computadora sin cámara: seguimos solo con micrófono y vemos al otro igual
    if (error?.name === "NotFoundError" || error?.name === "OverconstrainedError") {
      try {
        return await navigator.mediaDevices.getUserMedia({ audio: MEDIOS.audio });
      } catch { /* abajo avisamos */ }
    }
    const negado = error?.name === "NotAllowedError" || error?.name === "SecurityError";
    toast(t(negado ? "permissions_needed" : "call_failed"));
    return null;
  }
}

function crearConexion(l) {
  const pc = new RTCPeerConnection({ iceServers: ICE_SERVERS });
  l.pc = pc;
  for (const pista of l.local.getTracks()) {
    const emisor = pc.addTrack(pista, l.local);
    if (pista.kind === "video") l.emisorVideo = emisor;
  }
  // Sin cámara propia igual pedimos el video del otro
  if (l.soyQuienLlama && !l.emisorVideo) pc.addTransceiver("video", { direction: "recvonly" });

  // Mis candidatos: candidatos_de si llamo, candidatos_para si contesto
  const misCandidatos = collection(db, "videollamadas", l.callId, l.soyQuienLlama ? "candidatos_de" : "candidatos_para");
  pc.onicecandidate = (e) => {
    const c = e.candidate;
    if (!c || !c.candidate || l.terminada) return;   // null = ya no hay más candidatos
    addDoc(misCandidatos, { candidate: c.candidate, sdpMid: c.sdpMid ?? null, sdpMLineIndex: c.sdpMLineIndex ?? 0 })
      .catch(() => {});
  };

  pc.ontrack = (e) => {
    if (l.terminada) return;
    const video = $("#call-remote");
    // Lo normal es que audio y video lleguen en el mismo stream; si no, armamos uno
    let flujo = e.streams[0];
    if (!flujo) {
      l.remoto = l.remoto || new MediaStream();
      l.remoto.addTrack(e.track);
      flujo = l.remoto;
      video.srcObject = null;   // obliga a Safari a tomar la pista nueva
    }
    if (video.srcObject !== flujo) video.srcObject = flujo;
    video.play().catch(() => {});
    if (e.track.kind === "video") {
      l.hayVideoRemoto = true;
      if (l.inicio) $("#call-waiting").hidden = true;
    }
  };

  // Safari viejo no tiene connectionState: el estado ICE dice lo mismo
  if ("connectionState" in pc) pc.onconnectionstatechange = () => revisarConexion(l);
  else pc.oniceconnectionstatechange = () => revisarConexion(l);
}

function revisarConexion(l) {
  if (l.terminada) return;
  const s = "connectionState" in l.pc ? l.pc.connectionState : l.pc.iceConnectionState;
  if (s === "connected" || s === "completed") {
    clearTimeout(l.temporizadorConectar);
    clearTimeout(l.temporizadorCorte);
    l.temporizadorCorte = null;
    // El avatar se queda si el otro no tiene cámara
    if (l.hayVideoRemoto) $("#call-waiting").hidden = true;
    if (!l.inicio) {
      l.inicio = Date.now();
      pintarReloj(l);
      l.reloj = setInterval(() => pintarReloj(l), 1000);
    }
  } else if (s === "disconnected") {
    // Un corte breve (cambio de Wi-Fi a datos) se recupera solo; si dura, colgamos
    if (!l.temporizadorCorte) {
      l.temporizadorCorte = setTimeout(() => terminarLlamada(l, t("call_connection_lost")), SEGUNDOS_DESCONECTADO * 1000);
    }
  } else if (s === "failed") {
    terminarLlamada(l, t("call_failed_network"));
  }
}

// Ya hay oferta y respuesta: si no se encuentran (el otro se cerró a la fuerza, sus candidatos
// nunca llegaron) ICE puede quedarse "conectando" para siempre
function esperarConexion(l) {
  clearTimeout(l.temporizadorConectar);
  l.temporizadorConectar = setTimeout(() => terminarLlamada(l, t("call_failed_network")), SEGUNDOS_PARA_CONECTAR * 1000);
}

function pintarReloj(l) {
  const s = Math.floor((Date.now() - l.inicio) / 1000);
  $("#call-status").textContent = `${String(Math.floor(s / 60)).padStart(2, "0")}:${String(s % 60).padStart(2, "0")}`;
}

// "Llamando…" / "Conectando…" hasta que arranca el cronómetro
function ponerEstadoLlamada(l, clave) {
  if (!l.inicio && !l.terminada) $("#call-status").textContent = t(clave);
}

function agregarCandidato(l, d) {
  if (l.terminada || !d?.candidate) return;
  let candidato;
  try {
    candidato = new RTCIceCandidate({ candidate: d.candidate, sdpMid: d.sdpMid ?? null, sdpMLineIndex: d.sdpMLineIndex ?? null });
  } catch {
    return;   // mal formado: lo ignoramos
  }
  if (l.remotaLista) l.pc.addIceCandidate(candidato).catch(() => {});
  else l.cola.push(candidato);
}

// Los candidatos solo se pueden agregar después de la descripción remota
function vaciarCola(l) {
  l.remotaLista = true;
  for (const c of l.cola.splice(0)) l.pc.addIceCandidate(c).catch(() => {});
}

function escucharCandidatos(l) {
  const suyos = collection(db, "videollamadas", l.callId, l.soyQuienLlama ? "candidatos_para" : "candidatos_de");
  l.quitar.push(onSnapshot(suyos, (snap) => {
    for (const cambio of snap.docChanges()) {
      if (cambio.type === "added") agregarCandidato(l, cambio.doc.data());
    }
  }, () => {}));
}

// El documento de la llamada trae la respuesta (a quien llama) y el aviso de que el otro colgó
function escucharLlamada(l) {
  l.quitar.push(onSnapshot(doc(db, "videollamadas", l.callId), async (snap) => {
    const d = snap.data();
    if (!d || l.terminada) return;
    if (d.colgada === true) return terminarLlamada(l, "", { marcarColgada: false });
    if (!l.soyQuienLlama || !d.answer?.sdp || l.respuestaRecibida) return;

    l.respuestaRecibida = true;
    clearTimeout(l.temporizadorRespuesta);
    esperarConexion(l);
    ponerEstadoLlamada(l, "call_connecting");
    try {
      await l.pc.setRemoteDescription({ type: "answer", sdp: d.answer.sdp });
      vaciarCola(l);
    } catch {
      terminarLlamada(l, t("call_failed"));
    }
  }, () => {}));
}

/* ---------- Pantalla de llamada ---------- */

function abrirPantallaLlamada(l, clave) {
  $("#call-title").textContent = l.otroNombre;
  $("#call-status").textContent = t(clave);
  $("#call-waiting").hidden = false;
  ponerAvatarDe($("#call-avatar"), l.otroUid, l.otroNombre);
  $("#call-remote").srcObject = null;
  $("#call").hidden = false;
  // El chat de atrás queda fuera del alcance del teclado y del lector de pantalla
  $("#app").inert = true;
  $("#call [data-action=hangup]").focus();
  mostrarVistaPrevia(l);
  pintarControles(l);
  revisarCamaras(l);
}

// Mi cámara en la ventanita, en espejo salvo que sea la trasera
function mostrarVistaPrevia(l) {
  const propio = $("#call-local");
  const pista = l.local.getVideoTracks()[0];
  propio.hidden = !pista || !pista.enabled;
  propio.classList.toggle("espejo", pista?.getSettings?.().facingMode !== "environment");
  propio.srcObject = pista ? new MediaStream([pista]) : null;
  propio.play().catch(() => {});   // iOS a veces no arranca solo aunque tenga autoplay
}

function pintarControles(l) {
  const mic = l.local.getAudioTracks()[0];
  const cam = l.local.getVideoTracks()[0];
  marcarApagado($("#call-mic"), !mic || !mic.enabled);
  marcarApagado($("#call-camera"), !cam || !cam.enabled);
  $("#call-camera").hidden = !cam;
}

function marcarApagado(boton, apagado) {
  boton.classList.toggle("off", apagado);
  boton.setAttribute("aria-pressed", String(apagado));
}

// El botón de cambiar cámara solo aparece si hay más de una
async function revisarCamaras(l) {
  const boton = $("#call-switch");
  boton.hidden = true;
  if (!l.local.getVideoTracks().length) return;
  try {
    const camaras = (await navigator.mediaDevices.enumerateDevices()).filter((d) => d.kind === "videoinput");
    if (!l.terminada) boton.hidden = camaras.length < 2;
  } catch { /* sin lista de cámaras: nos quedamos con la actual */ }
}

function alternarMicrofono() {
  const pista = llamada?.local?.getAudioTracks()[0];
  if (!pista) return;
  pista.enabled = !pista.enabled;
  pintarControles(llamada);
}

function alternarCamara() {
  const pista = llamada?.local?.getVideoTracks()[0];
  if (!pista) return;
  pista.enabled = !pista.enabled;   // el otro ve negro; la cámara sigue lista para volver al instante
  pintarControles(llamada);
  mostrarVistaPrevia(llamada);
}

// Celular: alterna frontal / trasera. Computadora (aunque su webcam diga "frontal"):
// pasa a la siguiente cámara de la lista
async function cambiarCamara() {
  const l = llamada;
  const actual = l?.local?.getVideoTracks()[0];
  if (!actual || !l.emisorVideo || l.cambiandoCamara) return;
  l.cambiandoCamara = true;
  const ajustes = actual.getSettings?.() || {};
  const tamano = { width: MEDIOS.video.width, height: MEDIOS.video.height };
  const abrir = async (video) => (await navigator.mediaDevices.getUserMedia({ video })).getVideoTracks()[0];
  actual.stop();   // iOS no deja usar dos cámaras a la vez
  let nueva = null;
  try {
    if (ajustes.facingMode) {
      const otroLado = ajustes.facingMode === "environment" ? "user" : "environment";
      nueva = await abrir({ ...tamano, facingMode: { exact: otroLado } }).catch(() => null);
    }
    if (!nueva) {
      const camaras = (await navigator.mediaDevices.enumerateDevices()).filter((d) => d.kind === "videoinput");
      const i = camaras.findIndex((c) => c.deviceId === ajustes.deviceId);
      nueva = await abrir({ ...tamano, deviceId: { exact: camaras[(i + 1) % camaras.length].deviceId } });
    }
  } catch {
    // No se pudo abrir otra: volvemos a la de antes
    const antes = ajustes.deviceId ? { ...tamano, deviceId: { exact: ajustes.deviceId } } : MEDIOS.video;
    nueva = await abrir(antes).catch(() => null);
  }
  try {
    if (l.terminada) return nueva?.stop();
    // Sin ninguna (p. ej. iOS volvió a pedir permiso y se negó): soltamos la pista muerta
    // para que el otro no vea un cuadro congelado y no quede un botón de cámara "prendida"
    l.local.removeTrack(actual);
    if (nueva) {
      nueva.enabled = actual.enabled;   // si la cámara estaba apagada, sigue apagada
      l.local.addTrack(nueva);
    }
    await l.emisorVideo.replaceTrack(nueva);
  } catch {
    /* la conexión ya se cerró */
  } finally {
    l.cambiandoCamara = false;
    if (!l.terminada) {
      mostrarVistaPrevia(l);
      pintarControles(l);
      if (!nueva) revisarCamaras(l);
    }
  }
}

/* ---------- Colgar ---------- */

// Idempotente: el botón, el otro que colgó, un error o cerrar la pestaña pueden llegar juntos.
// Devuelve la promesa de los avisos al otro (cerrar sesión la espera antes de salir)
function terminarLlamada(l, aviso = "", { marcarColgada = true } = {}) {
  if (!l || l.terminada) return Promise.resolve();
  l.terminada = true;
  if (llamada === l) llamada = null;

  // Avisamos al otro; si aún le sonaba, que deje de sonar
  let avisos = Promise.resolve();
  if (marcarColgada && l.documentoCreado) {
    avisos = Promise.allSettled([
      setDoc(doc(db, "videollamadas", l.callId), { colgada: true }, { merge: true }),
      l.soyQuienLlama && cambiarAviso(l.otroUid, l.timestamp, LLAMADA.PERDIDA)
    ]);
  }

  l.quitar.forEach((fn) => fn());
  clearTimeout(l.temporizadorRespuesta);
  clearTimeout(l.temporizadorConectar);
  clearTimeout(l.temporizadorCorte);
  clearInterval(l.reloj);
  if (l.pc) {
    l.pc.onicecandidate = l.pc.ontrack = l.pc.onconnectionstatechange = l.pc.oniceconnectionstatechange = null;
    try { l.pc.close(); } catch { /* ya estaba cerrada */ }
  }
  l.local?.getTracks().forEach((pista) => pista.stop());   // apaga la luz de la cámara

  $("#call-remote").srcObject = null;
  $("#call-local").srcObject = null;
  $("#call").hidden = true;
  $("#app").inert = false;
  if (aviso) toast(aviso, 4000);
  return avisos;
}

// Cambia el aviso de timbre solo si sigue sonando ESTA llamada: no pisa una llamada nueva
// (quien llama colgó y volvió a llamar) ni revive una que ya terminó
async function cambiarAviso(uidQueRecibe, timestamp, nuevoEstado) {
  const ref = doc(db, "llamadas", uidQueRecibe);
  try {
    const d = (await getDoc(ref)).data();
    if (d && d.timestamp === timestamp && d.estado === LLAMADA.SONANDO) {
      await updateDoc(ref, { estado: nuevoEstado });
    }
  } catch { /* sin conexión: se vence sola a los 40 s */ }
}

// Cerrar o recargar la pestaña cuelga (lo que alcance a salir antes de que se cierre)
window.addEventListener("pagehide", () => terminarLlamada(llamada));

/* ---------- Quien llama ---------- */

async function llamar() {
  const miUid = estado.usuario?.uid;
  if (!miUid || !chat.otroUid) return toast(t("call_failed"));
  if (llamada) return;   // ya hay una llamada abierta (o pidiendo permisos)

  const chatId = chat.chatId;
  const l = nuevaLlamada({ soyQuienLlama: true, otroUid: chat.otroUid, otroNombre: chat.otroNombre || "Usuario" });
  llamada = l;

  // Sin cámara y micrófono no se crea nada en Firestore
  const local = await pedirMedios();
  if (l.terminada) {   // se cerró la sesión mientras pedíamos permisos
    local?.getTracks().forEach((pista) => pista.stop());
    return;
  }
  if (!local) return terminarLlamada(l, "", { marcarColgada: false });
  l.local = local;
  l.timestamp = Date.now();
  l.callId = `${chatId}_${l.timestamp}`;

  abrirPantallaLlamada(l, "call_calling");
  // Por si el otro nunca responde (teléfono apagado, sin internet). Corre desde ya: sin red,
  // Firestore no confirma las escrituras de abajo y nos quedaríamos "Llamando…" para siempre
  l.temporizadorRespuesta = setTimeout(
    () => terminarLlamada(l, t("call_missed", l.otroNombre)),
    SEGUNDOS_SIN_RESPUESTA * 1000
  );
  try {
    crearConexion(l);
  } catch {
    return terminarLlamada(l, t("call_failed"));
  }
  escucharCandidatos(l);

  try {
    const oferta = await l.pc.createOffer();
    await l.pc.setLocalDescription(oferta);
    await setDoc(doc(db, "videollamadas", l.callId), {
      de: miUid, para: l.otroUid, timestamp: l.timestamp, offer: { type: "offer", sdp: oferta.sdp }
    });
    l.documentoCreado = true;
    if (l.terminada) return;
    // Recién ahora suena el otro: cuando conteste, la oferta ya lo está esperando
    const miNombre = nombreVisible(estado.perfil || { email: estado.usuario.email });
    await setDoc(doc(db, "llamadas", l.otroUid), {
      de: miUid, deNombre: miNombre, sala: l.callId, estado: LLAMADA.SONANDO, timestamp: l.timestamp
    });
  } catch (error) {
    return terminarLlamada(l, mensajeDeError(error));
  }
  if (l.terminada) return;
  guardarMensaje(t("chat_notice_call"), chatId, l.otroUid).catch(() => {});

  escucharLlamada(l);
  // Lo que hace el otro con el timbre: contestó, rechazó o no contestó
  l.quitar.push(onSnapshot(doc(db, "llamadas", l.otroUid), (snap) => {
    const d = snap.data();
    if (!d || d.timestamp !== l.timestamp || l.terminada) return;
    if (d.estado === LLAMADA.ACEPTADA) return ponerEstadoLlamada(l, "call_connecting");
    if (l.respuestaRecibida) return;   // ya contestó: un cambio tardío del timbre no corta
    if (d.estado === LLAMADA.RECHAZADA) terminarLlamada(l, t("call_rejected", l.otroNombre));
    else if (d.estado === LLAMADA.PERDIDA) terminarLlamada(l, t("call_missed", l.otroNombre));
  }, () => {}));
}

/* ---------- Quien recibe (después de tocar "Contestar") ---------- */

async function contestar(d) {
  const miUid = estado.usuario.uid;
  if (llamada) return;
  const responder = (nuevoEstado) => cambiarAviso(miUid, d.timestamp, nuevoEstado);

  // El callId es "uidMenor_uidMayor_timestamp" de este chat y de este timbre
  // (una sala de Jitsi de las versiones viejas, "AppLaboratorio_uidA_uidB", no sirve)
  if (d.sala !== `${chatIdEntre(miUid, d.de)}_${d.timestamp}`) {
    responder(LLAMADA.PERDIDA);
    return toast(t("call_failed"));
  }

  const l = nuevaLlamada({
    soyQuienLlama: false, callId: d.sala, otroUid: d.de, otroNombre: d.deNombre || "Usuario", timestamp: d.timestamp
  });
  // Quien llama crea el documento antes de hacer sonar: desde ya, colgar le avisa
  l.documentoCreado = true;
  llamada = l;

  const local = await pedirMedios();
  if (l.terminada) {   // se cerró la sesión mientras pedíamos permisos
    local?.getTracks().forEach((pista) => pista.stop());
    return;
  }
  if (!local) {
    // Sin permisos no podemos contestar: a quien llama le deja de sonar
    responder(LLAMADA.PERDIDA);
    return terminarLlamada(l, "", { marcarColgada: false });
  }
  l.local = local;

  abrirPantallaLlamada(l, "call_connecting");
  try {
    crearConexion(l);
  } catch {
    responder(LLAMADA.PERDIDA);
    return terminarLlamada(l, t("call_failed"), { marcarColgada: false });
  }
  responder(LLAMADA.ACEPTADA);

  const ref = doc(db, "videollamadas", l.callId);
  let datos;
  try {
    datos = (await getDoc(ref)).data();
  } catch (error) {
    return terminarLlamada(l, mensajeDeError(error), { marcarColgada: false });
  }
  if (l.terminada) return;
  if (!datos?.offer?.sdp) return terminarLlamada(l, t("call_failed"), { marcarColgada: false });
  if (datos.colgada) return terminarLlamada(l, t("call_ended"), { marcarColgada: false });
  esperarConexion(l);

  // Los candidatos que lleguen antes de la oferta esperan en la cola
  escucharCandidatos(l);
  escucharLlamada(l);
  try {
    await l.pc.setRemoteDescription({ type: "offer", sdp: datos.offer.sdp });
    vaciarCola(l);
    const respuesta = await l.pc.createAnswer();
    await l.pc.setLocalDescription(respuesta);
    await setDoc(ref, { answer: { type: "answer", sdp: respuesta.sdp } }, { merge: true });
  } catch {
    terminarLlamada(l, t("call_failed"));
  }
}

/* ---------- Llamada entrante ---------- */

const entrante = {
  timestamp: 0,
  mostradoTimestamp: 0,
  datos: null,
  temporizador: null
};

function revisarLlamadaEntrante(snap) {
  const d = snap.data();
  const enPantalla = !$("#incoming").hidden;

  // Quien llama colgó o ya respondimos: cerramos la pantalla
  if (enPantalla && (!d || d.estado !== LLAMADA.SONANDO || d.timestamp !== entrante.timestamp)) {
    cerrarEntrante();
  }
  if (!d || d.estado !== LLAMADA.SONANDO) return;

  const antiguedad = Date.now() - (d.timestamp || 0);
  if (antiguedad > (SEGUNDOS_TIMBRE + 20) * 1000) return;   // margen por relojes desfasados
  if (d.timestamp === entrante.mostradoTimestamp) return;
  if (llamada) return;                                      // ya estamos en una llamada: ocupado

  entrante.timestamp = entrante.mostradoTimestamp = d.timestamp;
  entrante.datos = d;
  mostrarEntrante(d);
}

// La inicial al instante y la foto cuando llegue
async function ponerAvatarDe(el, uid, nombre) {
  pintarAvatar(el, nombre, "", { colorear: false });
  try {
    const perfil = await getDoc(doc(db, "usuarios", uid));
    if (perfil.exists()) pintarAvatar(el, nombre, perfil.data().foto, { colorear: false });
  } catch { /* sin foto */ }
}

function mostrarEntrante(d) {
  const nombre = d.deNombre || "Usuario";
  $("#incoming-name").textContent = nombre;
  ponerAvatarDe($("#incoming-avatar"), d.de, nombre);
  $("#incoming").hidden = false;
  empezarTimbre();
  if (!timbre.desbloqueado) $("#sound-chip").hidden = false;

  clearTimeout(entrante.temporizador);
  entrante.temporizador = setTimeout(() => responderEntrante(LLAMADA.PERDIDA), SEGUNDOS_TIMBRE * 1000);
}

function cerrarEntrante() {
  clearTimeout(entrante.temporizador);
  detenerTimbre();
  $("#incoming").hidden = true;
}

async function responderEntrante(nuevoEstado) {
  const d = entrante.datos;
  entrante.datos = null;   // un doble toque no contesta dos veces
  cerrarEntrante();
  if (!d || !estado.usuario) return;
  // Contestar abre nuestra pantalla de llamada (pide cámara y micrófono y marca "aceptada")
  if (nuevoEstado === LLAMADA.ACEPTADA) return contestar(d);
  try {
    await updateDoc(doc(db, "llamadas", estado.usuario.uid), { estado: nuevoEstado });
  } catch { /* si falla, la llamada se vence sola */ }
}

/* =====================================================================
   Sesión
   ===================================================================== */

function iniciarListenersDeSesion(uid) {
  const quitarPerfil = onSnapshot(doc(db, "usuarios", uid), (snap) => {
    estado.perfil = snap.exists() ? snap.data() : null;
    if (estado.vista === "home") pintarInicio();
    if (estado.vista === "perfil") rellenarPerfil();
  }, () => {});

  const quitarLlamadas = onSnapshot(doc(db, "llamadas", uid), revisarLlamadaEntrante, () => {});

  estado.limpiezaSesion.push(quitarPerfil, quitarLlamadas);

  // Recordatorio para habilitar el sonido del timbre
  if (!timbre.desbloqueado) $("#sound-chip").hidden = false;
}

function terminarListenersDeSesion() {
  estado.limpiezaSesion.forEach((fn) => fn());
  estado.limpiezaSesion = [];
  estado.perfil = null;
  cerrarEntrante();
  terminarLlamada(llamada);
  $("#sound-chip").hidden = true;
}

// Primero colgamos, con la sesión todavía abierta: después de signOut las reglas rechazan el
// aviso al otro y le seguiría sonando. Si no hay red, no esperamos más de 2 s
async function cerrarSesion() {
  await Promise.race([terminarLlamada(llamada), new Promise((listo) => setTimeout(listo, 2000))]);
  await signOut(auth);
  toast(t("logout_done"));
}

onAuthStateChanged(auth, (usuario) => {
  const antes = estado.usuario?.uid;
  estado.usuario = usuario;
  if (antes !== usuario?.uid) {
    terminarListenersDeSesion();
    if (usuario) iniciarListenersDeSesion(usuario.uid);
  }
  estado.arrancado = true;
  $("#view-splash").hidden = true;
  mostrarRuta();
});

/* =====================================================================
   Botones con data-goto / data-action
   ===================================================================== */

document.addEventListener("click", (e) => {
  const destino = e.target.closest("[data-goto]");
  if (destino) {
    e.preventDefault();
    ir(destino.dataset.goto);
    return;
  }
  const accion = e.target.closest("[data-action]")?.dataset.action;
  switch (accion) {
    case "lang":
      alternarIdioma();
      traducirPagina();
      mostrarRuta();
      break;
    case "forgot":
      abrirRecuperar();
      break;
    case "close-reset":
      dialogoReset.close();
      break;
    case "logout":
      cerrarSesion();
      break;
    case "call":
      llamar();
      break;
    case "hangup":
      terminarLlamada(llamada);
      break;
    case "toggle-mic":
      alternarMicrofono();
      break;
    case "toggle-camera":
      alternarCamara();
      break;
    case "switch-camera":
      cambiarCamara();
      break;
    case "accept":
      desbloquearAudio();
      responderEntrante(LLAMADA.ACEPTADA);
      break;
    case "reject":
      responderEntrante(LLAMADA.RECHAZADA);
      break;
    case "pick-photo":
      $("#perfil-file").click();
      break;
    case "remove-photo":
      fotoEditada = "";
      fotoTocada = true;
      pintarFotoPerfil();
      break;
  }
});

traducirPagina();
