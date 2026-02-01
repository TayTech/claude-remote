# Piano di Rebrand: RemoteCli for Claude

## Obiettivo
Rinominare l'applicazione da "Claude Remote" a "RemoteCli for Claude" per conformità trademark, cambiare schema colori e aggiungere disclaimer legale.

---

## 1. Cambio Nome App

### Android App

| File | Modifica |
|------|----------|
| `android/app/src/main/res/values/strings.xml` | `app_name` → "RemoteCli for Claude" |
| `android/app/src/main/res/values/strings.xml` | `service_channel_name` → "RemoteCli Connection" |
| `android/app/src/main/res/values/strings.xml` | `service_notification_title` → "RemoteCli for Claude" |
| `android/app/build.gradle.kts` | Verificare `applicationId` (opzionale: `com.remotecli.claude`) |

### Backend

| File | Modifica |
|------|----------|
| `backend/src/index.ts` | Messaggi di log e QR code description |
| `backend/package.json` | `name` field |

### Documentazione

| File | Modifica |
|------|----------|
| `README.md` | Titolo e tutte le occorrenze di "Claude Remote" |
| `LICENSE` | Se menziona il nome |

---

## 2. Cambio Colore (Arancione → Blu)

**Colore attuale:** `#D97757` (arancione Anthropic)
**Nuovo colore:** `#4CC9F0` (blu tech)

### File da modificare

| File | Modifica |
|------|----------|
| `android/app/src/main/res/values/ic_launcher_background.xml` | `#D97757` → `#4CC9F0` |
| `android/app/src/main/res/values/themes.xml` | Eventuali riferimenti al colore primario |
| `android/app/src/main/java/.../ui/theme/Color.kt` | Se esiste, aggiornare palette |

### Verifica visiva
- [ ] Icona launcher leggibile su sfondo blu
- [ ] Contrasto sufficiente con icona bianca terminale

---

## 3. Aggiunta Disclaimer

### Posizioni disclaimer

#### README.md (inizio file, dopo titolo)
```markdown
> **Disclaimer**: This is an UNOFFICIAL third-party client and is NOT affiliated with,
> endorsed by, or sponsored by Anthropic, PBC. "Claude" and "Claude Code" are trademarks
> of Anthropic, PBC. This application is provided for personal, non-commercial use only.
```

#### Android App - About/Settings Screen
Aggiungere sezione "About" con:
```
RemoteCli for Claude v1.0

⚠️ UNOFFICIAL APPLICATION
This is a third-party client and is NOT affiliated with,
endorsed by, or sponsored by Anthropic, PBC.

"Claude" and "Claude Code" are trademarks of Anthropic, PBC.

For personal, non-commercial use only.
```

#### Backend Startup Message
```
RemoteCli for Claude - Backend Server
⚠️ Unofficial third-party client - Not affiliated with Anthropic
```

---

## 4. Checklist Implementazione

### Fase 1: Rinomina
- [ ] Aggiornare `strings.xml` con nuovo nome
- [ ] Aggiornare `README.md`
- [ ] Aggiornare `package.json` backend
- [ ] Aggiornare messaggi startup backend

### Fase 2: Colori
- [ ] Modificare `ic_launcher_background.xml`
- [ ] Verificare/aggiornare `themes.xml`
- [ ] Verificare/aggiornare `Color.kt` (se presente)
- [ ] Rebuild app e verificare icona

### Fase 3: Disclaimer
- [ ] Aggiungere disclaimer a `README.md`
- [ ] Aggiungere schermata About in app Android
- [ ] Aggiungere disclaimer a startup backend

### Fase 4: Verifica Finale
- [ ] Build Android app senza errori
- [ ] Verificare icona su device
- [ ] Verificare nome app nel launcher
- [ ] Verificare nome in notifiche
- [ ] Test connessione funzionante
- [ ] Review README completo

---

## 5. File Coinvolti (Riepilogo)

```
android/
├── app/
│   ├── build.gradle.kts                    # applicationId (opzionale)
│   └── src/main/
│       ├── res/
│       │   └── values/
│       │       ├── strings.xml             # Nome app
│       │       ├── ic_launcher_background.xml  # Colore icona
│       │       └── themes.xml              # Colori tema
│       └── java/com/.../
│           └── ui/
│               ├── theme/Color.kt          # Palette colori (se esiste)
│               └── screens/settings/       # About dialog
│
backend/
├── package.json                            # Nome pacchetto
└── src/
    └── index.ts                            # Messaggi startup

README.md                                   # Documentazione
LICENSE                                     # Se menziona nome
```

---

## 6. Note Aggiuntive

### Package ID Android
L'`applicationId` attuale è probabilmente `com.clauderemote`.
**Opzioni:**
1. **Mantenere** `com.clauderemote` - Più semplice, nessun impatto su installazioni esistenti
2. **Cambiare** a `com.remotecli.claude` - Più coerente, ma richiede reinstallazione

**Raccomandazione:** Mantenere `com.clauderemote` per semplicità (è interno, non visibile agli utenti).

### Colore Alternativo
Se il blu `#4CC9F0` risulta troppo chiaro, alternative:
- `#2E86AB` - Blu più scuro
- `#0077B6` - Blu intenso
- `#023E8A` - Blu navy

---

## 7. Stima Effort

| Fase | Complessità |
|------|-------------|
| Rinomina | Bassa - Solo stringhe |
| Colori | Bassa - 2-3 file XML |
| Disclaimer | Media - Nuovo componente UI |
| Testing | Media - Rebuild e verifica |

**Totale stimato:** ~1-2 ore di lavoro

---

*Piano creato: 2 Febbraio 2026*
