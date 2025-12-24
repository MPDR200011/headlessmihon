import logging
import os
import subprocess
import requests
from bottle import Bottle, request

RUNTIME_ROOT = os.environ["RUNTIME_ROOT"]

APK_DIR = f"{RUNTIME_ROOT}/extension_apks"

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

app = Bottle()

def get_needed_extensions():
    data_host = os.environ["LARAVEL_HOST"]
    res = requests.get(f'{data_host}/extensions/needed')
    print(res.content)
    return res.json()

def download_apk(apk_name: str) -> str:
    apk_request_url = f"https://github.com/keiyoushi/extensions/raw/refs/heads/repo/apk/{apk_name}"
    logger.info(f"GET: {apk_request_url}")
    apk_request = requests.get(apk_request_url)

    apk_file_path = f"{APK_DIR}/{apk_name}"
    with open(apk_file_path, mode='wb') as apk_file:
        apk_file.write(apk_request.content)

    return apk_file_path


def install_apk(apk_file_path: str) -> subprocess.CompletedProcess:
    return subprocess.run(["adb", "install", apk_file_path])

def download_and_install_apk(apk_name: str):
    logger.info(f'Installing apk {apk_name}')
    apk_location = download_apk(apk_name)
    result = install_apk(apk_location)
    os.remove(apk_location)
    if result.returncode != 0:
        logger.error('Installation failed')

@app.route('/extensions/sync', method='POST')
def install_extensions():
    extensions_to_download = request.json

    if (extensions_to_download == None):
        return

    for extension in extensions_to_download:
        apk_name = extension['apk']
        logger.info(f'Installing apk {apk_name}')
        download_and_install_apk(apk_name)
        logger.info(f'done')

def main():
    logger.info("Starting up server")

    subprocess.run(["adb", "shell", "settings put secure install_non_market_apps 1"])
    res = install_apk(os.environ['APK_LOCATION'])
    if (res.returncode != 0):
        raise RuntimeError("Failed to install service APK")

    subprocess.run(["adb", "forward", "tcp:9000", "tcp:8080"])
    subprocess.run(["adb", "shell", "am", "start-foreground-service com.example.source_service/.SourceService"])

    os.makedirs(APK_DIR, exist_ok=True)

    logger.info("Getting needed extensions")
    for extension in get_needed_extensions():
        download_and_install_apk(extension['apk'])
    logger.info("All needed extensions installed")

    app.run(host='0.0.0.0', port=8090, server='cheroot')


if __name__ == "__main__":
    main()
