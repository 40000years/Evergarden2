"""A local pack mirror that answers every request only after a delay, like a slow CDN.

The pack-order probe uses it to check that players never get a pack with only the server's own
address while the public copy is still being verified. With drop-server, requests from the Minecraft
server itself (User-Agent Evergarden-pack-mirror) are cut off unanswered, as on a host that blocks
outbound HTTPS, while players are still served.

    python tests/arkhaia/slow_mirror.py DIRECTORY PORT DELAY_SECONDS [drop-server]
"""
import functools
import sys
import time
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer


class SlowHandler(SimpleHTTPRequestHandler):
    delay = 0.0
    drop_server = False

    def send_head(self):
        if self.drop_server and self.headers.get('User-Agent') == 'Evergarden-pack-mirror':
            self.close_connection = True
            self.connection.close()
            return None
        time.sleep(self.delay)
        return super().send_head()

    def log_message(self, format, *args):
        sys.stderr.write('%s %s\n' % (time.strftime('%H:%M:%S'), format % args))


def main():
    directory, port, delay = sys.argv[1], int(sys.argv[2]), float(sys.argv[3])
    SlowHandler.delay = delay
    SlowHandler.drop_server = sys.argv[4:] == ['drop-server']
    server = ThreadingHTTPServer(('127.0.0.1', port), functools.partial(SlowHandler, directory=directory))
    server.serve_forever()


if __name__ == '__main__':
    main()
