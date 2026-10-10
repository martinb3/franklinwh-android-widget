# FranklinWH Android Widget

An Android home-screen widget that shows FranklinWH home-battery stats (state of
charge, solar, load, grid and battery power, daily energy totals) by polling the
aGate gateway directly over your local network.

This is an unofficial project. It is not affiliated with, endorsed by, or
supported by FranklinWH.

## Status

Pre-alpha. Nothing is usable yet.

## Configuration

The widget will be configured with the gateway's host (IP address or hostname)
and TCP port on your LAN. No cloud account or credentials are involved.

## Security note

The gateway's local protocol has no authentication. Anyone on your LAN who can
reach the gateway's TCP port 9000 can read its data, including its serial number.
This app does not change that; it only reads what the gateway already exposes.
Consider isolating the gateway on a network segment you trust.

## Attribution

The local protocol implementation is a Kotlin port of
[franklinwh-local](https://pypi.org/project/franklinwh-local/) (MIT License),
which in turn builds on the original protocol work by voidstarr.

## License

BSD-3-Clause. See [LICENSE](LICENSE).
