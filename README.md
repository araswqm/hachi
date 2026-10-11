<p align="center">
  <img src="assets/logo.png" alt="Hachi logo" width="160">
</p>

# Hachi

Hachi is a chat app for Android where you talk with **AI characters you create yourself**.

You give a character a name, a personality, a backstory and an opening line, and from then on Hachi plays that role — remembering what you talked about, staying in character, and even messaging you first from time to time. Everything runs on your own AI provider account, so your conversations stay between you and the model you chose.

> Hachi is a personal project, shared as-is. It is not published on any store.
>
> It is a vibe-coding project, developed with [Claude Code](https://claude.com/claude-code). This is the first version, so expect bugs and some important features that are still missing.

---

## Requirements

- An Android phone running **Android 13 (API 33) or newer**.
- An **API key** from at least one AI provider (see [Setting up an AI provider](#setting-up-an-ai-provider)). These are usually paid services, depending on the provider you use. You use your own account and pay the provider directly.

---

## Installing

1. Copy the `hachi-*.apk` file to your phone.
2. Open it and allow installation from unknown sources when Android asks.
3. Launch **Hachi**.

---

## Setting up an AI provider

Hachi works with five providers: **DeepSeek, OpenAI (ChatGPT), Anthropic (Claude), Google Gemini and Mistral**.

1. Get an API key from the provider's website.
2. In Hachi, open **Settings → AI providers**.
3. Pick a provider, paste its **API key**, and leave it **Enabled**.
4. Optionally set the model name and base URL — the defaults usually work already.

Hachi also has a **multi-provider fallback system**, so you can add more than one. Hachi tries them **in the order shown on the settings screen**, and if the first one fails (out of credit, server error, no connection), it **automatically moves on to the next one** and tells you which provider answered.

You can also enter a **key per provider** — useful if you want to use a cheap model for summaries and a smarter one for chatting, or simply to have a backup ready.

---

## Creating a character

From the home screen, tap **+** in the bottom-right corner.

You can either:
- **Fill it in yourself** — name, description, personality, scenario, first message, example dialogues. A preview shows what the character will say when a chat begins.
- **Import a character card** — tap the import button and pick a PNG character card. Hachi understands the common card formats (V1, V2 and V3). The character's name, description and avatar are filled in for you.

You can give a character an **avatar photo** from your gallery, and later **export** it back out as a shareable card.

---

## Chatting

Open a character and start typing. A few things worth knowing:

- **Replies stream in** as they're written, so you don't wait for the whole message.
- **Regenerate** re-rolls the last answer. Every new answer is kept as a **variant** you can flip back and forth between — you can generate as many as you like and pick your favourite.
- **Edit or delete any message** you or the character sent. Deleting an early message quietly updates what the character remembers, so the story stays consistent.
- **✨ Asteroid** — the sparkle button next to the text box opens a small assistant with two jobs:
  - **Enhance my draft** — rewrites what you typed, keeping the character's voice and everything that happened so far in the chat.
  - **Write for me** — writes a reply in your place, based on the character's last message.
- **Markdown** is rendered: bold, italics, headings, lists, quotes and code blocks all show up formatted rather than as raw symbols.

### Keeping the conversation going

- **Proactive messages** — turn this on for a character and it will message you on its own, following up on things you talked about. You can set quiet hours so it doesn't ping you at night, and grant a battery-optimization exemption in Settings so those messages arrive on time.
- **Multiple chats per character** — tap the chat icon in the top bar to start a **new conversation** with the same character, or switch back to an older one. Each conversation has its own history and its own memory, and can even use a **different persona**. Your home screen still lists each character once — chats live inside the character.

### Memory

Hachi keeps a running memory of each conversation: a short summary of what has happened, how the relationship stands, how the character feels, and the topics it wants to bring up later. You never have to manage this — it's written automatically as you chat, and it's what lets a conversation keep its history even after hundreds of messages.

---

## Personas

A **persona** is *you* — your name and a short description of who you are. When a character talks to you, it uses the persona assigned to that chat, so you can be "Alex the astronomy nerd" in one conversation and "Sam" in another.

Manage them in **Settings → Personas** — each one can have its own avatar photo too.

---

## Making it yours

**Settings** has everything:

- **Appearance** — light, dark, follow the system, or **Night Owl**: a true OLED-black theme with soft light text, easy on the eyes in the dark. Optional dynamic colour tints the app to match your wallpaper.
- **Language** — English, Türkçe, Español, Português or Bahasa Indonesia. Applied instantly.
- **Haptics** — a light tick as messages stream in and as you tap.
- **AI providers** — your keys, models and their priority order.

---

## Your data and privacy

- **Your API keys never leave your phone** except when talking to the provider you chose. They are encrypted on-device using the Android system keystore and are excluded from backups.
- **Your conversations stay on your phone.** Hachi has no server of its own and no account to sign in to. Messages, characters, personas and memories are stored locally in the app.
- Nothing is collected, and nothing is sent anywhere except the AI provider that generates your replies.

---

## Tips

- Write a good **character description** — the more specific the personality and speaking style, the better the roleplay. Good example dialogues help a lot.
- If a character drifts out of character, **regenerate** a few times and pick the best variant.
- Give it a few conversations before judging the memory — it gets noticeably better once there's something to remember.

---

## License

Hachi is released under the **PolyForm Noncommercial License 1.0.0**. In short:

- You may **use, change and share** it freely for **any noncommercial purpose** — personal use, study, hobby projects, charity, education and research all count.
- You may **fork it and build on it**, as long as your work stays noncommercial.
- You may **not** sell it, bundle it into a paid product, or otherwise use it commercially.

It is *source-available*, not OSI open source — that's deliberate, so people can learn from and improve it without it being repackaged and sold.

See [`LICENSE`](LICENSE) for the full terms.
