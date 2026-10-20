// FAMessage web: misma funcionalidad y misma base de datos que la app Android.
// Firestore:
//   usuarios/{uid}                    { email, nombre, foto, rol }
//   chats/{uidA_uidB}                 { participantes, ultimoMensaje, ultimoTimestamp, ultimoEmisor, ultimoLeido }
//   chats/{uidA_uidB}/mensajes/{id}   { texto, emisorId, timestamp, leido }
//   llamadas/{uidQueRecibe}           { de, deNombre, sala, estado, timestamp }

import { initializeApp } from "https://www.gstatic.com/firebasejs/10.12.5/firebase-app.js";
import {
  getAuth, onAuthStateChanged, signInWithEmailAndPassword, createUserWithEmailAndPassword,
  sendPasswordResetEmail, sendEmailVerification, signOut
} from "https://www.gstatic.com/firebasejs/10.12.5/firebase-auth.js";
import {
  getFirestore, doc, getDoc, setDoc, updateDoc, collection, addDoc, query, where, orderBy,
  onSnapshot, writeBatch
} from "https://www.gstatic.com/firebasejs/10.12.5/firebase-firestore.js";

import { firebaseConfig, SERVIDOR_JITSI } from "./firebase-config.js";
import { t, idioma, alternarIdioma, traducirPagina } from "./i18n.js";

const app = initializeApp(firebaseConfig);
const auth = getAuth(app);
const db = getFirestore(app);

// Videollamada: mismos valores que Videollamada.kt en Android
const LLAMADA = { SONANDO: "sonando", ACEPTADA: "aceptada", RECHAZADA: "rechazada", PERDIDA: "perdida" };
const SEGUNDOS_TIMBRE = 40;
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
  mensajes: [],
  llamadaTimestamp: 0
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

async function guardarMensaje(texto) {
  const miUid = estado.usuario.uid;
  const ahora = Date.now();
  const chatRef = doc(db, "chats", chat.chatId);
  await Promise.all([
    addDoc(collection(chatRef, "mensajes"), { texto, emisorId: miUid, timestamp: ahora, leido: false }),
    // Resumen para la lista de conversaciones
    setDoc(chatRef, {
      participantes: [miUid, chat.otroUid],
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
   Videollamadas (Jitsi en el navegador)
   ===================================================================== */

let jitsi = null;
let cargaJitsi = null;
let alColgar = null;

function cargarJitsi() {
  if (window.JitsiMeetExternalAPI) return Promise.resolve();
  cargaJitsi = cargaJitsi || new Promise((resolve, reject) => {
    const script = document.createElement("script");
    script.src = `https://${SERVIDOR_JITSI}/external_api.js`;
    script.onload = resolve;
    script.onerror = () => { cargaJitsi = null; reject(new Error("jitsi")); };
    document.head.appendChild(script);
  });
  return cargaJitsi;
}

// Precargamos el script de Jitsi para que la llamada abra rápido
setTimeout(() => cargarJitsi().catch(() => {}), 3000);

async function abrirVideollamada(sala, titulo, onColgar) {
  const capa = $("#call");
  $("#call-title").textContent = titulo;
  $("#call-external").href = `https://${SERVIDOR_JITSI}/${sala}#config.prejoinConfig.enabled=false&userInfo.displayName=%22${encodeURIComponent(nombreVisible(estado.perfil || { email: estado.usuario.email }))}%22`;
  capa.hidden = false;
  alColgar = onColgar || null;

  try {
    await cargarJitsi();
  } catch {
    cerrarVideollamada();
    toast(t("call_failed"));
    return;
  }

  $("#call-frame").innerHTML = "";
  jitsi = new window.JitsiMeetExternalAPI(SERVIDOR_JITSI, {
    roomName: sala,
    parentNode: $("#call-frame"),
    width: "100%",
    height: "100%",
    lang: idioma(),
    userInfo: { displayName: nombreVisible(estado.perfil || { email: estado.usuario.email }) },
    configOverwrite: {
      prejoinPageEnabled: false,
      prejoinConfig: { enabled: false },
      disableDeepLinking: true,
      startWithAudioMuted: false,
      startWithVideoMuted: false,
      enableWelcomePage: false,
      enableClosePage: false,
      lobby: { autoKnock: true }
    },
    interfaceConfigOverwrite: {
      MOBILE_APP_PROMO: false,
      SHOW_JITSI_WATERMARK: false,
      SHOW_CHROME_EXTENSION_BANNER: false
    }
  });
  jitsi.addListener("readyToClose", cerrarVideollamada);
}

function cerrarVideollamada() {
  if (jitsi) {
    try { jitsi.dispose(); } catch { /* ya estaba cerrada */ }
    jitsi = null;
  }
  $("#call-frame").innerHTML = "";
  $("#call").hidden = true;
  const fn = alColgar;
  alColgar = null;
  fn?.();
}

// Llamar desde el chat: hace sonar al otro y deja aviso en la conversación
async function llamar() {
  const miUid = estado.usuario?.uid;
  if (!miUid || !chat.otroUid) return toast(t("call_failed"));

  const sala = `AppLaboratorio_${chat.chatId}`;
  const miNombre = nombreVisible(estado.perfil || { email: estado.usuario.email });
  const ahora = Date.now();
  const refLlamada = doc(db, "llamadas", chat.otroUid);
  const otroNombre = chat.otroNombre;
  chat.llamadaTimestamp = ahora;

  try {
    await setDoc(refLlamada, { de: miUid, deNombre: miNombre, sala, estado: LLAMADA.SONANDO, timestamp: ahora });
  } catch (error) {
    return toast(mensajeDeError(error));
  }
  guardarMensaje(t("chat_notice_call")).catch(() => {});

  // Avisamos si nos rechazan o no contestan
  const quitar = onSnapshot(refLlamada, (snap) => {
    const d = snap.data();
    if (!d || d.timestamp !== ahora) return;
    if (d.estado === LLAMADA.RECHAZADA) toast(t("call_rejected", otroNombre), 4000);
    else if (d.estado === LLAMADA.PERDIDA) toast(t("call_missed", otroNombre), 4000);
    else if (d.estado !== LLAMADA.ACEPTADA) return;
    quitar();
  }, () => {});

  abrirVideollamada(sala, otroNombre, async () => {
    // Colgamos antes de que contestara: dejamos de hacerlo sonar
    try {
      const snap = await getDoc(refLlamada);
      const d = snap.data();
      if (d && d.timestamp === ahora && d.estado === LLAMADA.SONANDO) {
        await updateDoc(refLlamada, { estado: LLAMADA.PERDIDA });
      }
    } catch { /* sin conexión: se vence sola a los 40 s */ }
  });
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
  if (!$("#call").hidden) return;                           // ya estamos en una llamada

  entrante.timestamp = entrante.mostradoTimestamp = d.timestamp;
  entrante.datos = d;
  mostrarEntrante(d);
}

async function mostrarEntrante(d) {
  const nombre = d.deNombre || "Usuario";
  $("#incoming-name").textContent = nombre;
  pintarAvatar($("#incoming-avatar"), nombre, "", { colorear: false });
  $("#incoming").hidden = false;
  empezarTimbre();
  if (!timbre.desbloqueado) $("#sound-chip").hidden = false;

  clearTimeout(entrante.temporizador);
  entrante.temporizador = setTimeout(() => responderEntrante(LLAMADA.PERDIDA), SEGUNDOS_TIMBRE * 1000);

  // Foto de quien llama
  try {
    const perfil = await getDoc(doc(db, "usuarios", d.de));
    if (perfil.exists()) pintarAvatar($("#incoming-avatar"), nombre, perfil.data().foto, { colorear: false });
  } catch { /* sin foto */ }
}

function cerrarEntrante() {
  clearTimeout(entrante.temporizador);
  detenerTimbre();
  $("#incoming").hidden = true;
}

async function responderEntrante(nuevoEstado) {
  const d = entrante.datos;
  cerrarEntrante();
  if (!d || !estado.usuario) return;
  try {
    await updateDoc(doc(db, "llamadas", estado.usuario.uid), { estado: nuevoEstado });
  } catch { /* si falla, la llamada se vence sola */ }
  if (nuevoEstado === LLAMADA.ACEPTADA) abrirVideollamada(d.sala, d.deNombre || "");
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
  if (!$("#call").hidden) cerrarVideollamada();
  $("#sound-chip").hidden = true;
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
      signOut(auth).then(() => toast(t("logout_done")));
      break;
    case "call":
      llamar();
      break;
    case "hangup":
      cerrarVideollamada();
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
