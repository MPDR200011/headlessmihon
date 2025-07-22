#!/usr/bin/env python3

import os
import requests

RUNTIME_ROOT = os.environ["RUNTIME_ROOT"]

APK_DIR = f"{RUNTIME_ROOT}/extension_apks"

PKGS_TO_INSTALL = {
    "eu.kanade.tachiyomi.extension.all.mangadex",
    "eu.kanade.tachiyomi.extension.all.mangafire"
}

EXTENSION_REPO = "https://raw.githubusercontent.com/keiyoushi/extensions/repo/index.min.json"

def main():
    r = requests.get(EXTENSION_REPO)

    data = r.json()

    os.makedirs(APK_DIR, exist_ok=True)

    for ext in data:
        if ext["pkg"] in PKGS_TO_INSTALL:
            apkName = ext["apk"]
            apkRequestURL = f"https://github.com/keiyoushi/extensions/raw/refs/heads/repo/apk/{apkName}"
            print(f"GET: {apkRequestURL}")
            apkRequest = requests.get(apkRequestURL)
            with open(f"{APK_DIR}/{apkName}", mode='wb') as apkFile:
                apkFile.write(apkRequest.content)


if __name__ == "__main__":
  main()
