"""One-shot: encrypt the 4 MoneyPal signing secrets with the repo's Actions
public key (libsodium sealed box) and PUT them as GitHub Actions secrets.
Reads keystore.properties from the repo root; keystore is base64'd.
Secret values are never printed.
"""
import base64
import json
import sys
import urllib.request
from pathlib import Path

from nacl import encoding, public

REPO = "sachit1751-art/MoneyPal"
ROOT = Path(__file__).resolve().parent.parent


def main() -> int:
    # Git-stored credentials, same way the shell obtains them.
    import subprocess
    raw = subprocess.run(
        ["git", "credential", "fill"],
        input="protocol=https\nhost=github.com\n\n",
        capture_output=True, text=True, check=True,
    ).stdout
    tok = next(
        line.split("=", 1)[1].strip()
        for line in raw.splitlines()
        if line.startswith("password=")
    )

    props = dict(
        line.split("=", 1)
        for line in (ROOT / "keystore.properties").read_text().splitlines()
        if "=" in line
    )
    keystore_b64 = base64.b64encode((ROOT / "release-keystore.jks").read_bytes()).decode()

    secrets = {
        "MONEYPAL_RELEASE_KEYSTORE_BASE64": keystore_b64,
        "MONEYPAL_RELEASE_STORE_PASSWORD": props["storePassword"],
        "MONEYPAL_RELEASE_KEY_ALIAS": props["keyAlias"],
        "MONEYPAL_RELEASE_KEY_PASSWORD": props["keyPassword"],
    }

    api = f"https://api.github.com/repos/{REPO}/actions/secrets"
    headers = {"Authorization": f"token {tok}", "Accept": "application/vnd.github+json"}

    req = urllib.request.Request(f"{api}/public-key", headers=headers)
    pk_data = json.load(urllib.request.urlopen(req))
    key_id, key = pk_data["key_id"], pk_data["key"]

    recipient = public.PublicKey(key.encode(), encoding.Base64Encoder())
    sealed = public.SealedBox(recipient)

    for name, value in secrets.items():
        enc = sealed.encrypt(value.encode())
        body = json.dumps({
            "encrypted_value": base64.b64encode(enc).decode(),
            "key_id": key_id,
        }).encode()
        put = urllib.request.Request(
            f"{api}/{name}", data=body, headers=headers, method="PUT"
        )
        with urllib.request.urlopen(put) as resp:
            print(f"{name}: HTTP {resp.status}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
