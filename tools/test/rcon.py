"""A minimal RCON client for a test server.

    python tools/test/rcon.py [--port 25575] [--password mstest] <command>...
    python tools/test/rcon.py -f tools/test/scenarios/basic.txt

A scenario file has one command per line; '#' starts a comment, 'sleep N' waits N seconds.
"""
import argparse
import socket
import struct
import sys
import time


def _packet(request_id, kind, body):
    data = body.encode("utf-8") + b"\x00\x00"
    return struct.pack("<iii", len(data) + 8, request_id, kind) + data


def _read(sock):
    header = b""
    while len(header) < 4:
        chunk = sock.recv(4 - len(header))
        if not chunk:
            raise ConnectionError("the server closed the connection")
        header += chunk
    size = struct.unpack("<i", header)[0]
    data = b""
    while len(data) < size:
        chunk = sock.recv(size - len(data))
        if not chunk:
            raise ConnectionError("the server closed the connection")
        data += chunk
    request_id, _ = struct.unpack("<ii", data[:8])
    return request_id, data[8:-2].decode("utf-8", "replace")


class Rcon:
    def __init__(self, port=25575, password="mstest", host="127.0.0.1", timeout=600):
        self.sock = socket.create_connection((host, port), timeout=timeout)
        self.sock.sendall(_packet(1, 3, password))
        request_id, _ = _read(self.sock)
        if request_id == -1:
            raise PermissionError("wrong RCON password")

    def command(self, command):
        self.sock.sendall(_packet(2, 2, command))
        return _read(self.sock)[1]

    def close(self):
        self.sock.close()


def read_scenario(path):
    """The commands of a scenario file, with 'sleep N' kept as such."""
    lines = []
    with open(path, encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if line and not line.startswith("#"):
                lines.append(line)
    return lines


def run(rcon, lines, out=sys.stdout):
    """Runs the lines, printing each command and its answer. Returns the transcript."""
    transcript = []
    for line in lines:
        if line.startswith("sleep "):
            time.sleep(float(line.split()[1]))
            continue
        answer = rcon.command(line)
        transcript.append((line, answer))
        print("> " + line, file=out)
        if answer:
            print(answer, file=out)
    return transcript


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--port", type=int, default=25575)
    parser.add_argument("--password", default="mstest")
    parser.add_argument("-f", "--file", action="append", default=[], help="scenario file (repeatable)")
    parser.add_argument("commands", nargs="*")
    args = parser.parse_args()
    lines = [line for path in args.file for line in read_scenario(path)] + args.commands
    client = Rcon(args.port, args.password)
    try:
        run(client, lines)
    finally:
        client.close()
