# Pollinations for Minecraft Servers

A PaperMC plugin that adds Pollinations AI to your Minecraft server: AI chat, persona NPCs, and text-to-image map art. Each server owner uses **their own Pollinations API key**, so players never need one.

**Pollinations QUEST #17246**

## Features

- `/poll chat <message>` — talk to an AI assistant with per-player conversation memory
- `/poll npc <persona> <message>` — talk to in-game persona NPCs (blacksmith, wizard, merchant — add your own in `config.yml`)
- `/poll image <prompt>` — turns a prompt into AI art on a real in-game map item (FLUX.1 Schnell)
- `/poll clear` — wipe your conversation history
- `/poll key <sk_...>` — admins set the server's API key in-game (or edit `config.yml`)
- Per-player chat memory (configurable history length)
- All API calls run async — zero server lag on the main thread

## Install

1. Download `Pollinations-1.0.0.jar` from [releases](../../releases)
2. Drop it into your server's `plugins/` folder
3. Start the server once, then edit `plugins/Pollinations/config.yml`:
   - Set `api-key` to your key from [enter.pollinations.ai/keys](https://enter.pollinations.ai/keys) (free keys work — Quest Pollen covers the default models)
   - Or run `/poll key <sk_...>` in-game as an admin
4. Restart or run `/reload confirm`

Works on Paper 1.21.x (and forks like Purpur, Pufferfish).

## Commands & Permissions

| Command | Description | Permission |
|---|---|---|
| `/poll chat <message>` | Talk to the AI | `pollinations.use` (default: everyone) |
| `/poll npc <persona> <message>` | Talk to a persona NPC | `pollinations.use` |
| `/poll image <prompt>` | Generate a map from a prompt | `pollinations.use` |
| `/poll clear` | Clear your chat history | `pollinations.use` |
| `/poll key <key>` | Set the API key | `pollinations.admin` (default: op) |
| `/poll about` | Plugin info | `pollinations.use` |

## Configuration

```yaml
api-key: ""                 # your sk_... key
text-model: "openai/gpt-5.4-nano"
image-model: "black-forest-labs/flux.1-schnell"
image-size: 512
max-history: 10
personas:                   # any key here becomes /poll npc <key>
  blacksmith: "You are Grondar, a gruff dwarven blacksmith..."
  wizard: "You are Elowen, an ancient wizard..."
  merchant: "You are Silt, a travelling merchant..."
```

Text and image models can be any from the [live catalog](https://gen.pollinations.ai/v1/models). Both defaults run on free Quest Pollen.

## Build from source

```bash
mvn package   # Java 21, produces target/Pollinations-1.0.0.jar
```

## License

MIT — built for Pollinations QUEST #17246