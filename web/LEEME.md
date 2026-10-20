# FAMessage web

Versión web de la app: misma base de datos (Firebase `app-famessage`), mismas cuentas y
chats que la app Android. Se instala en iPhone desde Safari: **Compartir → Agregar a pantalla de inicio**.

## Probar en tu computadora

Desde la raíz del repositorio:

```
npx serve web
```

Abre `http://localhost:3000`. La cámara funciona en `localhost`, pero **no** desde el celular
apuntando a la IP de tu computadora: Safari exige HTTPS para cámara y micrófono.

## Publicar (Firebase Hosting, gratis en el plan Spark)

Desde la raíz del repositorio:

```
npx firebase-tools login
npx firebase-tools deploy --only hosting
```

Queda en **https://app-famessage.web.app**. Para actualizarla, repite el `deploy`.

Para publicar también las reglas de Firestore: `npx firebase-tools deploy --only firestore:rules`.

## Archivos

- `index.html`: todas las pantallas
- `app.js`: lógica (Firebase Auth, Firestore, llamadas, Jitsi)
- `i18n.js`: textos en español e inglés
- `firebase-config.js`: datos del proyecto de Firebase y servidor Jitsi
- `styles.css`: diseño (modo claro y oscuro)
