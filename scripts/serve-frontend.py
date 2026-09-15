"""Loopback-only server for the existing Vite build, with a same-origin API proxy."""
from http.server import ThreadingHTTPServer, SimpleHTTPRequestHandler
from http.client import HTTPConnection
from pathlib import Path
from urllib.parse import urlsplit
import os

DIST = Path(__file__).resolve().parents[1] / 'frontend' / 'dist'
HOP_HEADERS = {'connection', 'transfer-encoding', 'keep-alive', 'upgrade', 'proxy-authenticate', 'proxy-authorization', 'te', 'trailer'}

class Handler(SimpleHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=str(DIST), **kwargs)

    def proxy(self):
        try:
            size = int(self.headers.get('Content-Length', '0'))
            if size < 0 or size > 1048576:
                self.send_error(413)
                return
            body = self.rfile.read(size) if size else None
            headers = {k: v for k, v in self.headers.items() if k.lower() not in HOP_HEADERS | {'host'}}
            connection = HTTPConnection('127.0.0.1', int(os.getenv('BACKEND_PORT', '8080')), timeout=20)
            try:
                connection.request(self.command, self.path, body=body, headers=headers)
                response = connection.getresponse()
                content = response.read()
                self.send_response(response.status)
                for k, v in response.getheaders():
                    if k.lower() not in HOP_HEADERS | {'content-length'}:
                        self.send_header(k, v)
                self.send_header('Content-Length', str(len(content)))
                self.end_headers()
                if self.command != 'HEAD':
                    self.wfile.write(content)
            finally:
                connection.close()
        except (OSError, ValueError):
            self.send_error(502, 'Backend is not available')

    def do_GET(self):
        if urlsplit(self.path).path.startswith('/api/'):
            return self.proxy()
        candidate = Path(self.translate_path(self.path))
        if not candidate.is_file() and not Path(urlsplit(self.path).path).suffix:
            self.path = '/index.html'
        super().do_GET()

    def do_POST(self):
        if urlsplit(self.path).path.startswith('/api/'):
            return self.proxy()
        self.send_error(405)

    do_PUT = do_POST
    do_DELETE = do_POST

if __name__ == '__main__':
    if not (DIST / 'index.html').exists():
        raise SystemExit('Build frontend first: npm run build in frontend/')
    print('Frontend: http://127.0.0.1:5173', flush=True)
    ThreadingHTTPServer(('127.0.0.1', 5173), Handler).serve_forever()
