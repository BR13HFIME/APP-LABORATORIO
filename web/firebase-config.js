// Proyecto de Firebase de FAMessage (el mismo que usa la app Android).
// Si registras una app Web en la consola de Firebase, puedes pegar aquí su configuración;
// para Auth y Firestore bastan estos datos.
export const firebaseConfig = {
  apiKey: "AIzaSyBvLoShGWk7gCOI8fIKV1YnC6pc5Ium_Pc",
  authDomain: "app-famessage.firebaseapp.com",
  projectId: "app-famessage",
  storageBucket: "app-famessage.firebasestorage.app",
  messagingSenderId: "374120873651"
};

// Servidores ICE de la videollamada (la misma lista que la app Android).
// El STUN solo le dice a cada dispositivo su IP pública; no pasa audio ni video por él.
// Si se deja la lista vacía, la llamada solo funciona con los dos dispositivos en la misma red Wi-Fi.
// No hay servidor TURN: el audio y el video van siempre directo entre los dos dispositivos.
export const ICE_SERVERS = [{ urls: "stun:stun.l.google.com:19302" }];
