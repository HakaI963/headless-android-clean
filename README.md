# Headless Android

A clean, Java-only Android app that runs installed apps on private virtual
displays and exposes one built-in MCP endpoint for an AI agent. No clones, no
external shell app, no second controller, no copied engine.

## Build

```sh
ANDROID_JAR=/path/android.jar BUILD_TOOLS=/path/build-tools/35.0.0 \
SYSROOT=/path/amd64-sysroot bash scripts/build-apk.sh
```

## What it does

- Creates real `VirtualDisplay`s and captures their frames.
- Uses Shizuku shell permission to start apps on those displays and inject input.
- Hosts Android's real `UiAutomation` in the shell process for `ui_tree`,
  `ui_find`, `ui_click` and `ui_set_text`.
- Serves MCP at `http://<phone-ip>:8765/mcp` with `X-Droid-Token` auth.

## Install

Copy the APK to `/data/local/tmp` and run:

```sh
cmd package install -r /data/local/tmp/headless-android-clean.apk
```

Shared Downloads storage cannot be parsed by the installer on some phones.
