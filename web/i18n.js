// Textos de la app en español e inglés (los mismos que la versión Android)

const textos = {
  es: {
    app_name: "FAMessage",
    app_tagline: "Chatea y haz videollamadas al instante",
    btn_change_language: "English",
    sign_in_title: "Iniciar Sesión",
    sign_in_subtitle: "Qué bueno verte de nuevo",
    hint_email: "Correo electrónico",
    hint_password: "Contraseña",
    hint_confirm_password: "Confirmar contraseña",
    hint_name: "Nombre",
    forgot_password: "¿Olvidaste tu contraseña?",
    btn_sign_in: "Iniciar Sesión",
    go_to_sign_up: "¿No tienes cuenta? Regístrate",
    sign_up_title: "Registrarse",
    sign_up_subtitle: "Crea tu cuenta en segundos",
    btn_sign_up: "Registrarse",
    go_to_sign_in: "¿Ya tienes una cuenta? Inicia sesión",
    hello_name: "¡Hola, {0}!",
    admin_badge: "Administrador",
    home_section_title: "¿Qué quieres hacer hoy?",
    home_chats_title: "Mensajes",
    home_chats_desc: "Conversa en tiempo real con tus contactos",
    home_video_title: "Videollamadas",
    home_video_desc: "Abre un chat y toca la cámara para llamar",
    home_profile_title: "Mi perfil",
    home_profile_desc: "Cambia tu nombre y tu foto",
    btn_logout: "Cerrar sesión",
    users_title: "Chats",
    users_subtitle: "Elige con quién quieres hablar",
    users_empty: "Todavía no hay otros usuarios registrados",
    users_load_error: "No se pudieron cargar los contactos",
    search_hint: "Buscar contacto…",
    user_item_hint: "Toca para chatear o videollamar",
    you_prefix: "Tú: {0}",
    yesterday: "Ayer",
    chat_subtitle: "Chat privado",
    chat_hint: "Escribe un mensaje…",
    chat_seen: "Visto",
    chat_notice_call: "📹 Inicié una videollamada",
    cd_back: "Atrás",
    cd_send: "Enviar",
    cd_video_call: "Iniciar videollamada",
    profile_title: "Mi perfil",
    profile_subtitle: "Así te ven tus contactos",
    profile_change_photo: "Cambiar foto",
    profile_remove_photo: "Quitar foto",
    profile_save: "Guardar cambios",
    profile_saved: "Perfil actualizado",
    profile_photo_error: "No se pudo cargar esa imagen",
    reset_title: "Recuperar contraseña",
    reset_message: "Te enviaremos un enlace a tu correo para crear una nueva contraseña.",
    reset_send: "Enviar enlace",
    reset_sent: "Listo. Revisa tu correo (también la carpeta de spam)",
    cancel: "Cancelar",
    error_required: "Este campo es obligatorio",
    error_email_invalid: "Escribe un correo válido",
    error_password_short: "Mínimo 6 caracteres",
    error_passwords_mismatch: "Las contraseñas no coinciden",
    error_wrong_credentials: "Correo o contraseña incorrectos",
    error_email_in_use: "Ese correo ya está registrado",
    error_no_internet: "Sin conexión a internet",
    error_too_many_requests: "Demasiados intentos. Espera un momento",
    error_generic: "Algo salió mal: {0}",
    error_profile_read: "No se pudo leer tu perfil",
    sign_in_success: "Inicio de sesión exitoso",
    sign_up_success: "Cuenta creada. Te enviamos un correo para verificarla",
    logout_done: "Sesión cerrada",
    incoming_call: "Videollamada entrante…",
    incoming_accept: "Contestar",
    incoming_reject: "Rechazar",
    call_rejected: "{0} rechazó la llamada",
    call_missed: "{0} no contestó",
    call_failed: "No se pudo iniciar la videollamada",
    call_hangup: "Colgar",
    call_open_browser: "¿Sin cámara? Abrir aparte",
    permissions_needed: "Se necesitan permisos de cámara y micrófono",
    sound_enable: "🔔 Toca aquí para activar el timbre de llamadas"
  },
  en: {
    app_name: "FAMessage",
    app_tagline: "Chat and video call instantly",
    btn_change_language: "Español",
    sign_in_title: "Sign In",
    sign_in_subtitle: "Good to see you again",
    hint_email: "Email",
    hint_password: "Password",
    hint_confirm_password: "Confirm password",
    hint_name: "Name",
    forgot_password: "Forgot your password?",
    btn_sign_in: "Sign In",
    go_to_sign_up: "Don't have an account? Sign Up",
    sign_up_title: "Sign Up",
    sign_up_subtitle: "Create your account in seconds",
    btn_sign_up: "Sign Up",
    go_to_sign_in: "Already have an account? Sign In",
    hello_name: "Hi, {0}!",
    admin_badge: "Administrator",
    home_section_title: "What would you like to do today?",
    home_chats_title: "Messages",
    home_chats_desc: "Chat in real time with your contacts",
    home_video_title: "Video calls",
    home_video_desc: "Open a chat and tap the camera to call",
    home_profile_title: "My profile",
    home_profile_desc: "Change your name and photo",
    btn_logout: "Log out",
    users_title: "Chats",
    users_subtitle: "Choose who you want to talk to",
    users_empty: "There are no other users yet",
    users_load_error: "Could not load your contacts",
    search_hint: "Search contact…",
    user_item_hint: "Tap to chat or video call",
    you_prefix: "You: {0}",
    yesterday: "Yesterday",
    chat_subtitle: "Private chat",
    chat_hint: "Type a message…",
    chat_seen: "Seen",
    chat_notice_call: "📹 I started a video call",
    cd_back: "Back",
    cd_send: "Send",
    cd_video_call: "Start video call",
    profile_title: "My profile",
    profile_subtitle: "This is how your contacts see you",
    profile_change_photo: "Change photo",
    profile_remove_photo: "Remove photo",
    profile_save: "Save changes",
    profile_saved: "Profile updated",
    profile_photo_error: "Could not load that image",
    reset_title: "Reset password",
    reset_message: "We will email you a link to create a new password.",
    reset_send: "Send link",
    reset_sent: "Done. Check your inbox (and the spam folder)",
    cancel: "Cancel",
    error_required: "This field is required",
    error_email_invalid: "Enter a valid email",
    error_password_short: "At least 6 characters",
    error_passwords_mismatch: "Passwords do not match",
    error_wrong_credentials: "Wrong email or password",
    error_email_in_use: "That email is already registered",
    error_no_internet: "No internet connection",
    error_too_many_requests: "Too many attempts. Please wait a moment",
    error_generic: "Something went wrong: {0}",
    error_profile_read: "Could not read your profile",
    sign_in_success: "Signed in successfully",
    sign_up_success: "Account created. We sent you a verification email",
    logout_done: "Signed out",
    incoming_call: "Incoming video call…",
    incoming_accept: "Answer",
    incoming_reject: "Decline",
    call_rejected: "{0} declined the call",
    call_missed: "{0} did not answer",
    call_failed: "Could not start the video call",
    call_hangup: "Hang up",
    call_open_browser: "No camera? Open separately",
    permissions_needed: "Camera and microphone permissions are required",
    sound_enable: "🔔 Tap here to enable the call ringtone"
  }
};

const CLAVE = "famessage_idioma";

export function idioma() {
  try {
    return localStorage.getItem(CLAVE) === "en" ? "en" : "es";
  } catch {
    return "es";
  }
}

export function alternarIdioma() {
  try {
    localStorage.setItem(CLAVE, idioma() === "es" ? "en" : "es");
  } catch { /* sin almacenamiento: se queda en español */ }
}

export function t(clave, ...args) {
  const texto = textos[idioma()][clave] ?? textos.es[clave] ?? clave;
  return texto.replace(/\{(\d)\}/g, (_, i) => args[Number(i)] ?? "");
}

// Rellena todos los elementos marcados con data-i18n / data-i18n-placeholder / data-i18n-aria
export function traducirPagina() {
  document.documentElement.lang = idioma();
  document.querySelectorAll("[data-i18n]").forEach((el) => {
    el.textContent = t(el.dataset.i18n);
  });
  document.querySelectorAll("[data-i18n-placeholder]").forEach((el) => {
    el.placeholder = t(el.dataset.i18nPlaceholder);
  });
  document.querySelectorAll("[data-i18n-aria]").forEach((el) => {
    el.setAttribute("aria-label", t(el.dataset.i18nAria));
  });
}
