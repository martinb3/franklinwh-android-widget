# Regenerates app/src/test/resources/golden.json using the reference Python library.
#
#   python3 -m venv .venv
#   .venv/bin/pip install franklinwh-local==0.1.0a0
#   .venv/bin/python tools/gen_golden.py app/src/test/resources
#
# Only fake serials may appear here; never put a real gateway serial in this file.
"""Generate golden wire frames for the Kotlin protocol tests using franklinwh-local.

All serials here are fake. Run: python gen_golden.py <outdir>
"""
import json, sys, os
from franklinwh_local.frame import CommunicationObject, build_request
from franklinwh_local.encryption import derive_secret_key_int

out = sys.argv[1]; os.makedirs(out, exist_ok=True)
cases = []

# 1. Request frame the widget must emit: deviceDetailInfo (1301), dummy serial, fixed snno/timestamp.
req = build_request("deviceDetailInfo", "00000000", {"opt": 0}, snno=7, timestamp=1700000000)
cases.append({"name": "request_device_detail_dummy_sn", "equip_no": "00000000",
              "secret": derive_secret_key_int("00000000"), "frame_hex": req.hex(),
              "envelope": {"cmdType": 1301, "equipNo": "00000000", "type": 0, "timeStamp": 1700000000,
                           "snno": 7, "len": 9, "crc": None, "dataArea": {"opt": 0}}})

data = {"soc": 28.157896041870117, "fhpSoc": [27.9, 28.4], "p_sun": 1816, "p_load": 1991, "p_uti": -61,
        "p_fhp": 239, "p_gen": 0, "kwh_sun": 10.31427001953125, "kwh_load": 26.004329681396484,
        "kwh_uti_in": 19.018310546875, "kwh_uti_out": 0.08725738525390625,
        "kwh_fhp_chg": 3.419999122619629, "kwh_fhp_di": 0.1790001392364502, "kwh_gen": 0.0,
        "run_status": 1, "name": "aGate", "t_amb": 21.5, "mode": 2}

# 2. Response with a realistic-shaped fake serial; the widget sent a dummy SN so it must brute-force the key.
for sn in ["10060010A00X00000000", "00000000", "ZZ9-fake"]:
    obj = CommunicationObject(cmd=1302, equip_no=sn, type_=0, snno=7, timestamp=1700000001, data=data)
    cases.append({"name": f"response_device_detail_sn_{sn}", "equip_no": sn,
                  "secret": derive_secret_key_int(sn), "frame_hex": obj.to_wire().hex(),
                  "envelope": obj.envelope()})

# 3. Garbage: header ok, body not decodable with any secret.
bad = b'{"cmdType":1302,"equipNo":"00000000",' + bytes(range(40))
cases.append({"name": "response_undecodable", "equip_no": "00000000", "secret": None,
              "frame_hex": bad.hex(), "envelope": None, "expect_error": True})

for c in cases:
    if c["envelope"] and c["envelope"].get("crc") is None:
        from franklinwh_local.crc import crc32_hex
        c["envelope"]["crc"] = crc32_hex(json.dumps(c["envelope"]["dataArea"], separators=(",", ":")).encode())
json.dump(cases, open(os.path.join(out, "golden.json"), "w"), indent=1)
print(f"wrote {len(cases)} cases to {out}/golden.json")
