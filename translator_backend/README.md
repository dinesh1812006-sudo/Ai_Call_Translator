# AI Call Translator — Backend

FastAPI + Gemini translation backend for the Android AI Call Translator app.

## 1. Install locally

```bash
cd translator_backend
python -m venv .venv
# Windows:
.venv\Scripts\activate
# macOS/Linux:
# source .venv/bin/activate
pip install -r requirements.txt
```

## 2. Create `.env`

Copy the example and fill in your keys (never commit `.env`):

```bash
copy .env.example .env
```

`.env.example`:

```text
GEMINI_API_KEY=your_gemini_api_key_here
BACKEND_SECRET=your_custom_backend_secret_here
```

- `GEMINI_API_KEY` — from https://aistudio.google.com/
- `BACKEND_SECRET` — any strong random string. It must match the Android
  app's `X-App-Secret` header (`ApiService.kt` default is
  `change-me-in-production`). If you change one side, change the other.

## 3. Run locally

```bash
cd translator_backend
python -m uvicorn main:app --host 0.0.0.0 --port 8000
```

## 4. Test `/health` (no Gemini call, responds instantly)

```bash
curl http://localhost:8000/health
```

Expected:

```json
{"status": "ok"}
```

## 5. Test `/translate`

```bash
curl -X POST http://localhost:8000/translate ^
  -H "Content-Type: application/json" ^
  -H "X-App-Secret: <your BACKEND_SECRET>" ^
  -d "{\"text\": \"How are you?\", \"source_language\": \"en\", \"target_language\": \"ta\"}"
```

Expected:

```json
{"translated_text": "...", "error": null}
```

The endpoint also accepts `source_lang` / `target_lang` (used by the
Android app today) and `source_language` / `target_language`.

## 6. Deploy to Render

1. Push this project to a GitHub repository (verify with `git status` that
   `.env` and other secrets are NOT included).
2. Open https://dashboard.render.com/ → **New +** → **Web Service** →
   connect the GitHub repository.
   - The included `render.yaml` Blueprint fills in these values automatically;
     if configuring manually:
   - **Root Directory:** `translator_backend`
   - **Build Command:** `pip install -r requirements.txt`
   - **Start Command:** `uvicorn main:app --host 0.0.0.0 --port $PORT`
   - **Health Check Path:** `/health`
3. Add environment variables:
   - `GEMINI_API_KEY` = your Gemini key (required)
   - `BACKEND_SECRET` = must match the Android app's header value
4. Deploy, wait until status is **Live**.
5. Open `https://YOUR-BACKEND-NAME.onrender.com/health` and verify
   `{"status":"ok"}`.

## 7. Required environment variables

| Variable         | Required | Purpose                              |
|------------------|----------|--------------------------------------|
| `GEMINI_API_KEY` | yes      | Gemini API key (backend only, never in the app) |
| `BACKEND_SECRET` | yes      | Shared secret checked via `X-App-Secret` header |
| `PORT`           | no       | Provided automatically by Render     |

## 8. Configure the Android backend URL

In the app: **Settings → Backend API URL** → enter the cloud URL with
trailing slash, e.g. `https://YOUR-BACKEND-NAME.onrender.com/` → Save.
Rebuild/install, then test a translated call with the laptop backend
stopped.
