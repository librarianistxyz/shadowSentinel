#!/usr/bin/env node

/**
 * Shadow Edge Intelligence Feeder & Internet Crawler
 * 
 * Searches the web, crawls real-time global news wires, tech breakthroughs,
 * scientific discoveries, and philosophical ideas, then pins them into
 * the Cloudflare Edge KV store so Shadow fires in sub-second latency with live knowledge.
 * 
 * Usage:
 *   node feed_edge_knowledge.js
 *   node feed_edge_knowledge.js "quantum computing breakthroughs"
 *   node feed_edge_knowledge.js --continuous (runs periodically every 30 minutes)
 */

const https = require('https');
const http = require('http');

const CLOUDFLARE_PIN_ENDPOINT = 'https://librarianist.xyz/api/knowledge';

// Default search and crawl topics across diverse domains of human knowledge
const TOPIC_REGISTRY = [
  { topic: 'Artificial Intelligence and LLM breakthroughs', category: 'AI_TECH' },
  { topic: 'Global Breaking News and World Politics', category: 'WORLD_NEWS' },
  { topic: 'Space Exploration, NASA, and Astrophysics', category: 'SCIENCE' },
  { topic: 'Breakthrough Energy, Fusion, and Battery Technology', category: 'TECH' },
  { topic: 'Biotechnology, Longevity, and Neuroscience', category: 'MEDICINE' },
  { topic: 'Cybersecurity, Intrusion Detection, and Cryptography', category: 'SECURITY' },
  { topic: 'Modern Philosophy, Ethics of AI, and Consciousness', category: 'PHILOSOPHY' }
];

function fetchUrl(url, timeoutMs = 4500) {
  return new Promise((resolve, reject) => {
    const isHttps = url.startsWith('https');
    const client = isHttps ? https : http;
    const req = client.get(url, {
      headers: {
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36',
        'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,application/json,*/*;q=0.8'
      }
    }, (res) => {
      let data = '';
      res.on('data', chunk => data += chunk);
      res.on('end', () => resolve(data));
    });

    req.on('error', reject);
    req.setTimeout(timeoutMs, () => {
      req.destroy();
      reject(new Error(`Timeout fetching ${url}`));
    });
  });
}

function parseRssXml(xml, max = 5) {
  const items = [];
  const regex = /<item>([\s\S]*?)<\/item>/gi;
  let match;
  while ((match = regex.exec(xml)) !== null && items.length < max) {
    const block = match[1];
    const titleMatch = block.match(/<title>(?:<!\[CDATA\[)?([\s\S]*?)(?:\]\]>)?<\/title>/i);
    const descMatch = block.match(/<description>(?:<!\[CDATA\[)?([\s\S]*?)(?:\]\]>)?<\/description>/i);
    const sourceMatch = block.match(/<source[^>]*>(?:<!\[CDATA\[)?([\s\S]*?)(?:\]\]>)?<\/source>/i);

    if (titleMatch) {
      const title = titleMatch[1].replace(/<[^>]+>/g, '').replace(/&quot;/g, '"').replace(/&amp;/g, '&').trim();
      const desc = descMatch ? descMatch[1].replace(/<[^>]+>/g, '').replace(/&quot;/g, '"').replace(/&amp;/g, '&').trim() : '';
      const source = sourceMatch ? sourceMatch[1].replace(/<[^>]+>/g, '').trim() : '';
      items.push({ title, desc, source });
    }
  }
  return items;
}

async function searchAndCrawlTopic(topicObj) {
  const query = encodeURIComponent(topicObj.topic);
  const items = [];

  console.log(`\n[CRAWLER] Searching and browsing real-time knowledge: "${topicObj.topic}"...`);

  // 1. Google News Live RSS Feed
  try {
    const rssUrl = `https://news.google.com/rss/search?q=${query}&hl=en-US&gl=US&ceid=US:en`;
    const xmlData = await fetchUrl(rssUrl);
    const parsed = parseRssXml(xmlData, 3);
    for (const p of parsed) {
      items.push({
        topic: topicObj.topic,
        category: topicObj.category,
        source: p.source || 'Global News Wire',
        content: `${p.title}${p.desc && p.desc !== p.title ? ` — ${p.desc}` : ''}`
      });
    }
  } catch (err) {
    console.warn(`[WARN] RSS fetch error for ${topicObj.topic}:`, err.message);
  }

  // 2. DuckDuckGo Instant Knowledge & News API
  try {
    const ddgUrl = `https://api.duckduckgo.com/?q=${query}&format=json&no_html=1&skip_disambig=1`;
    const rawJson = await fetchUrl(ddgUrl);
    const ddg = JSON.parse(rawJson);
    if (ddg.AbstractText) {
      items.push({
        topic: topicObj.topic,
        category: topicObj.category,
        source: ddg.AbstractSource || 'DuckDuckGo Knowledge',
        content: ddg.AbstractText
      });
    }
  } catch (err) {
    // Ignore DDG errors
  }

  return items;
}

async function pinToCloudflareEdge(entry) {
  return new Promise((resolve) => {
    const postData = JSON.stringify(entry);
    const req = https.request(CLOUDFLARE_PIN_ENDPOINT, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Content-Length': Buffer.byteLength(postData)
      }
    }, (res) => {
      let data = '';
      res.on('data', chunk => data += chunk);
      res.on('end', () => {
        try {
          const resp = JSON.parse(data);
          resolve(resp);
        } catch(e) {
          resolve({ status: 'raw', data });
        }
      });
    });

    req.on('error', (err) => {
      console.warn('[EDGE PIN ERROR]:', err.message);
      resolve(null);
    });

    req.write(postData);
    req.end();
  });
}

async function runCrawlCycle(customQuery) {
  console.log(`==================================================================`);
  console.log(` [SHADOW EDGE INTELLIGENCE FEEDER] Initiating Web Knowledge Crawl`);
  console.log(` Target: ${CLOUDFLARE_PIN_ENDPOINT}`);
  console.log(` Time: ${new Date().toISOString()}`);
  console.log(`==================================================================`);

  const topics = customQuery 
    ? [{ topic: customQuery, category: 'USER_QUERY' }] 
    : TOPIC_REGISTRY;

  let totalPinned = 0;

  for (const t of topics) {
    const findings = await searchAndCrawlTopic(t);
    for (const item of findings) {
      process.stdout.write(`  -> Pinning to Cloudflare Edge KV: "${item.content.slice(0, 65)}..." `);
      const res = await pinToCloudflareEdge(item);
      if (res && res.status === 'pinned') {
        console.log(`[✓ PINNED]`);
        totalPinned++;
      } else {
        console.log(`[!]`);
      }
    }
  }

  console.log(`\n[✓ COMPLETE] Successfully crawled and pinned ${totalPinned} fresh intelligence entries directly into Cloudflare Edge KV memory.`);
}

// CLI Execution Entrypoint
const args = process.argv.slice(2);
const isContinuous = args.includes('--continuous');
const customTopic = args.find(a => !a.startsWith('--'));

if (isContinuous) {
  runCrawlCycle(customTopic);
  // Recurring schedule every 30 minutes
  setInterval(() => {
    runCrawlCycle(customTopic);
  }, 30 * 60 * 1000);
} else {
  runCrawlCycle(customTopic);
}
