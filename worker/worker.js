/**
 * Cloudflare Worker Backend for ProxyVPN
 * Предоставляет API для диагностики и агрегирует прокси в KV.
 */

export default {
  async fetch(request, env, ctx) {
    const url = new URL(request.url);
    const path = url.pathname;

    const corsHeaders = {
      'Access-Control-Allow-Origin': '*',
      'Content-Type': 'application/json; charset=utf-8',
    };

    try {
      if (path === '/api/check-ip') {
        const clientIp = request.headers.get('cf-connecting-ip') || 'unknown';
        const cfData = request.cf || {};
        
        return new Response(JSON.stringify({
          ip: clientIp,
          country: cfData.country || 'Unknown',
          city: cfData.city || 'Unknown',
          asn: cfData.asn || 'Unknown',
          asnOrganization: cfData.asOrganization || 'Unknown',
          latitude: cfData.latitude || 0,
          longitude: cfData.longitude || 0
        }), { headers: corsHeaders });
      }

      if (path === '/api/dns') {
        const name = url.searchParams.get('name');
        if (!name) return new Response('Missing "name" param', { status: 400 });

        const dohUrl = `https://cloudflare-dns.com/dns-query?name=${name}&type=A`;
        const dohRequest = new Request(dohUrl, {
          headers: { 'Accept': 'application/dns-json' }
        });
        
        const dohResponse = await fetch(dohRequest);
        const dohData = await dohResponse.json();
        
        return new Response(JSON.stringify(dohData), { headers: corsHeaders });
      }

      if (path === '/api/speedtest') {
        const buffer = new Uint8Array(2 * 1024 * 1024); 
        return new Response(buffer, {
          headers: {
            'Access-Control-Allow-Origin': '*',
            'Content-Type': 'application/octet-stream',
            'Content-Length': buffer.length.toString()
          }
        });
      }

      if (path === '/api/proxies') {
        if (!env.PROXY_KV) return new Response('KV not bound', { status: 500 });
        const cachedProxies = await env.PROXY_KV.get('latest_proxies');
        return new Response(cachedProxies || '[]', { headers: corsHeaders });
      }

      return new Response('Not Found', { status: 404 });
    } catch (err) {
      return new Response(JSON.stringify({ error: err.message }), { status: 500, headers: corsHeaders });
    }
  },

  async scheduled(event, env, ctx) {
    ctx.waitUntil(this.updateProxies(env));
  },

  async updateProxies(env) {
    if (!env.PROXY_KV) return;
    const sources = [
      "https://raw.githubusercontent.com/TheSpeedX/SOCKS-List/master/socks5.txt"
    ];
    
    let allProxies = [];
    for (const source of sources) {
      try {
        const resp = await fetch(source);
        const text = await resp.text();
        const lines = text.split('\n').filter(l => l.includes(':'));
        
        lines.forEach(line => {
          const parts = line.trim().split(':');
          if (parts.length >= 2) {
             const ip = parts[0];
             const port = parseInt(parts[1]);
             if (ip && port) allProxies.push({ host: ip, port: port, type: 'SOCKS5' });
          }
        });
      } catch (e) {
        console.error(`Failed to fetch from ${source}`, e);
      }
    }
    
    await env.PROXY_KV.put('latest_proxies', JSON.stringify(allProxies.slice(0, 1000)));
  }
};
