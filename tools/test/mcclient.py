"""A bare-bones 1.20.1 (protocol 763) offline-mode client for the scripted server test.

It logs in as a player, answers keep-alives, says it speaks MobStacker's config channel, asks for the
config snapshot and prints what the server sends back: every cfg_sync (the regions in it, and whether
it says the player may edit) and every chat line. It can also type commands as that player - which
RCON cannot do, since the console is always an operator.

    python tools/test/mcclient.py <name> [--port 25599] [--stay SECONDS] [--cmd "mobstacker region list" ...]
                       [--wait SECONDS] [--edit "region:farm:@enabled=false"] ...

Steps run in the order given, after the first snapshot arrives: --cmd sends a command (no slash),
--wait pauses, and the client stays connected for --stay seconds after the last step, printing
whatever arrives (a snapshot the server pushes on its own shows up there).
"""
import hashlib
import socket
import struct
import sys
import time
import uuid
import zlib

PROTOCOL = 763

# 1.20.1 packet ids
S_LOGIN_DISCONNECT, S_LOGIN_SUCCESS, S_SET_COMPRESSION, S_LOGIN_PLUGIN = 0x00, 0x02, 0x03, 0x04
P_COMMANDS, P_PLUGIN, P_DISCONNECT, P_KEEPALIVE, P_LOGIN, P_SYSTEM_CHAT = 0x10, 0x17, 0x1A, 0x23, 0x28, 0x64
P_SET_ENTITY_DATA = 0x52


def entity_name(r):
    """The custom name (index 2) and its "always shown" flag (index 3) in an entity-data packet, as far
    as the entries before them can be read; None when the packet has neither."""
    eid = r.varint()
    name = visible = None
    while r.readable():
        index = r.data[r.pos]
        r.pos += 1
        if index == 0xFF:
            break
        kind = r.varint()
        if kind == 0:
            r.bytes(1)
        elif kind == 1:
            r.varint()
        elif kind == 3:
            r.bytes(4)
        elif kind in (4, 5):
            r.utf()
        elif kind == 6:
            value = r.utf() if r.bool() else ''
            if index == 2:
                name = value
        elif kind == 8:
            value = r.bool()
            if index == 3:
                visible = value
        else:
            break  # a type this client does not read; what came before still counts
    if name is None and visible is None:
        return None
    return 'entity %d name=%s visible=%s' % (eid, name, visible)
C_CHAT_COMMAND, C_PLUGIN, C_KEEPALIVE = 0x04, 0x0D, 0x12


def varint(n):
    n &= 0xFFFFFFFF
    out = b''
    while True:
        b = n & 0x7F
        n >>= 7
        if n:
            out += bytes([b | 0x80])
        else:
            return out + bytes([b])


def string(s):
    data = s.encode('utf-8')
    return varint(len(data)) + data


class Reader:
    def __init__(self, data):
        self.data, self.pos = data, 0

    def varint(self):
        result = shift = 0
        while True:
            b = self.data[self.pos]
            self.pos += 1
            result |= (b & 0x7F) << shift
            if not b & 0x80:
                break
            shift += 7
        if result & 0x80000000:
            result -= 1 << 32
        return result

    def bytes(self, n):
        out = self.data[self.pos:self.pos + n]
        self.pos += n
        return out

    def utf(self):
        return self.bytes(self.varint()).decode('utf-8')

    def bool(self):
        return self.bytes(1) != b'\x00'

    def int(self):
        return struct.unpack('>i', self.bytes(4))[0]

    def readable(self):
        return self.pos < len(self.data)


class Conn:
    def __init__(self, host, port):
        self.sock = socket.create_connection((host, port))
        self.threshold = -1

    def send(self, pid, data=b''):
        body = varint(pid) + data
        if self.threshold >= 0:
            body = (varint(len(body)) + zlib.compress(body)) if len(body) >= self.threshold else varint(0) + body
        self.sock.sendall(varint(len(body)) + body)

    def _exact(self, n):
        out = b''
        while len(out) < n:
            chunk = self.sock.recv(n - len(out))
            if not chunk:
                raise ConnectionError('server closed the connection')
            out += chunk
        return out

    def recv(self):
        length = shift = 0
        while True:
            b = self._exact(1)[0]
            length |= (b & 0x7F) << shift
            if not b & 0x80:
                break
            shift += 7
        r = Reader(self._exact(length))
        if self.threshold >= 0:
            size = r.varint()
            rest = r.bytes(length)
            r = Reader(zlib.decompress(rest) if size > 0 else rest)
        return r.varint(), r


def log(name, text):
    print('[%6.2f] %s: %s' % (time.time() - START, name, text), flush=True)


def describe_sync(r):
    auth = r.bool()
    status = r.utf()
    settings = {}
    for _ in range(r.varint()):
        k = r.utf()
        settings[k] = r.utf()
    for _ in range(4):
        for _ in range(r.varint()):
            r.utf()
    for _ in range(r.varint()):
        r.utf()
        r.varint()
    regions = []
    for _ in range(r.varint()):
        name = r.utf()
        r.utf()
        r.utf()
        for _ in range(7):
            r.int()
        r.utf()
        for _ in range(r.varint()):
            r.utf()
            r.utf()
        for _ in range(4):
            if r.bool():
                for _ in range(r.varint()):
                    r.utf()
        for _ in range(r.varint()):
            r.utf()
            r.varint()
        regions.append(name)
    xray = r.readable() and r.bool()
    # 1.12.0: on/off and whole-dimension per region, in the same order
    flags = []
    if r.readable():
        for i in range(r.varint()):
            on, whole = r.bool(), r.bool()
            biomes = [r.utf() for _ in range(r.varint())]
            flags.append('%s:%s%s%s' % (regions[i], 'on' if on else 'OFF', ',whole' if whole else '',
                                        (',biomes=' + '|'.join(biomes)) if biomes else ''))
    return 'cfg_sync authorized=%s regions=%s privateRegions=%s xray=%s flags=%s status=%r' % (
        auth, sorted(regions), settings.get('privateRegions'), xray, flags, status)


def main(argv):
    global START
    START = time.time()
    name = argv[0]
    port, stay, steps, modded = 25597, 3.0, [], False
    i = 1
    while i < len(argv):
        if argv[i] == '--port':
            port = int(argv[i + 1])
        elif argv[i] == '--stay':
            stay = float(argv[i + 1])
        elif argv[i] == '--modded':
            # Also says it speaks the stack-label channel, as a game with the mod does.
            modded = True
            i += 1
            continue
        elif argv[i] in ('--cmd', '--wait', '--edit'):
            steps.append((argv[i], argv[i + 1]))
        i += 2

    c = Conn('127.0.0.1', port)
    c.send(0x00, varint(PROTOCOL) + string('127.0.0.1') + struct.pack('>H', port) + varint(2))
    # The offline-mode UUID, as the server works it out itself (UUID.nameUUIDFromBytes).
    offline = uuid.UUID(bytes=hashlib.md5(('OfflinePlayer:' + name).encode()).digest(), version=3)
    c.send(0x00, string(name) + b'\x01' + offline.bytes)
    while True:
        pid, r = c.recv()
        if pid == S_SET_COMPRESSION:
            c.threshold = r.varint()
        elif pid == S_LOGIN_PLUGIN:
            c.send(0x02, varint(r.varint()) + b'\x00')
        elif pid == S_LOGIN_DISCONNECT:
            log(name, 'refused at login: ' + r.utf())
            return 1
        elif pid == S_LOGIN_SUCCESS:
            break

    c.sock.settimeout(0.2)
    logged_in = synced = False
    login_at = None
    deadline = None
    step_at = 0.0
    while True:
        now = time.time()
        if deadline is not None and now >= deadline:
            break
        if logged_in and not synced and now - login_at > 3.0:
            # A Forge server never sends cfg_sync to this client (no FML handshake), so after a
            # moment the commands go out without it.
            log(name, 'no cfg_sync after 3 s (a Forge server?) - going on without it')
            synced = True
            step_at = now
            if not steps:
                deadline = now + stay
        if synced and steps and now >= step_at:
            kind, value = steps.pop(0)
            if kind == '--wait':
                step_at = now + float(value)
            elif kind == '--edit':
                # What the config GUI sends: "<id>=<value>", e.g. region:farm:@enabled=false
                key, _, raw = value.partition('=')
                log(name, 'edit ' + key + ' = ' + raw)
                c.send(C_PLUGIN, string('mobstacker:cfg_edit') + string(key) + string(raw))
                step_at = now + 0.5
            else:
                log(name, '> /' + value)
                c.send(C_CHAT_COMMAND, string(value) + struct.pack('>qq', int(now * 1000), 0)
                       + varint(0) + varint(0) + b'\x00\x00\x00')
                step_at = now + 0.5
            if not steps:
                deadline = max(step_at, now) + stay
        try:
            pid, r = c.recv()
        except socket.timeout:
            continue
        except ConnectionError as e:
            log(name, str(e))
            return 1
        if pid == P_KEEPALIVE:
            c.send(C_KEEPALIVE, r.bytes(8))
        elif pid == P_LOGIN and not logged_in:
            logged_in = True
            login_at = time.time()
            c.send(C_PLUGIN, string('minecraft:register') + b'mobstacker:cfg_sync'
                   + (b'\x00mobstacker:stack_label' if modded else b''))
            c.send(C_PLUGIN, string('mobstacker:cfg_request'))
        elif pid == P_PLUGIN:
            channel = r.utf()
            if channel == 'mobstacker:stack_label':
                eid, count = r.varint(), r.varint()
                fmt = r.utf()
                color, always = r.varint(), r.bool()
                log(name, 'label entity %d count=%d format=%r colour=%d always=%s' % (eid, count, fmt, color, always))
            elif channel == 'mobstacker:cfg_sync':
                log(name, describe_sync(r))
                if not synced:
                    synced = True
                    step_at = time.time() + 0.5
                    if not steps:
                        deadline = time.time() + stay
        elif pid == P_COMMANDS:
            # Sent at login and again whenever the server decides this player's commands changed.
            tree = r.bytes(len(r.data))
            log(name, 'command tree (%d bytes, mobstacker %s, share %s)' % (
                len(tree), b'mobstacker' in tree, b'share' in tree))
        elif pid == P_SET_ENTITY_DATA:
            seen = entity_name(r)
            if seen:
                log(name, seen)
        elif pid == P_SYSTEM_CHAT:
            text = r.utf()
            if not r.bool():
                log(name, 'chat ' + text)
        elif pid == P_DISCONNECT:
            log(name, 'kicked: ' + r.utf())
            return 1
    c.sock.close()
    return 0


if __name__ == '__main__':
    sys.exit(main(sys.argv[1:]))
