# Local AI

Local AI is a native Android app intended to become a genuinely offline, Siri-like personal assistant. It is an APK project, not a website, and it is text-only by design.

## Current development direction

- General-purpose on-device language model using LiteRT-LM: Qwen2 0.5B Instruct (about 647 MB).
- Model download requires internet once; after the model is stored on the phone, inference is intended to run locally.
- Purple floating orb with a compact request panel.
- The model can answer open-ended text requests; Android actions are restricted to explicit, permission-safe tools.
- The app must never claim a phone action succeeded unless the tool confirms it.

## Current limitations

- This is an early build, not a finished Siri replacement.
- The current Android tool set is limited mainly to opening selected apps and settings.
- General model responses, tool selection, model loading, and floating-orb behavior still require testing on a physical device.
- A 647 MB model may use around 1 GB or more of working memory depending on runtime/device. The app uses a conservative memory check, but the OPPO A17k (3 GB RAM) must be tested directly before we can claim reliable operation.
- It does not yet have broad file search, media conversion, unrestricted control of other apps, or a full multi-step phone automation system.
- It does not use cloud AI for inference. Network access is used for the one-time model download.

## Build

GitHub Actions builds a debug APK. A successful CI build proves the project compiles; it does not prove that the model loads or runs reliably on a real phone.
