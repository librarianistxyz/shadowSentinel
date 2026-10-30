import { Env } from './worker';

export interface DailyDigestRecord {
  digestDate: string;
  generatedAt: number;
  bulletCount: number;
  categories: Record<string, string[]>;
  bullets: string[];
  rawDigestText: string;
  sourceCount: number;
}

interface ScrapedItem {
  source: string;
  title: string;
  snippet: string;
  category: string;
}

// Fetch with timeout helper
async function fetchSafe(url: string, headers: Record<string, string> = {}, timeoutMs = 4500): Promise<string> {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  try {
    const res = await fetch(url, {
      headers: {
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36',
        'Accept': 'application/rss+xml, application/xml, text/xml, application/json, text/plain, */*',
        ...headers
      },
      signal: controller.signal
    });
    clearTimeout(timer);
    if (!res.ok) return '';
    return await res.text();
  } catch (e) {
    clearTimeout(timer);
    return '';
  }
}

// Parse RSS/XML items
function parseXmlItems(xml: string, sourceName: string, category: string, limit = 8): ScrapedItem[] {
  const items: ScrapedItem[] = [];
  const itemRegex = /<item[\s\S]*?>([\s\S]*?)<\/item>/gi;
  let match;
  while ((match = itemRegex.exec(xml)) !== null && items.length < limit) {
    const block = match[1];
    const titleMatch = block.match(/<title>(?:<!\[CDATA\[)?([\s\S]*?)(?:\]\]>)?<\/title>/i);
    const descMatch = block.match(/<description>(?:<!\[CDATA\[)?([\s\S]*?)(?:\]\]>)?<\/description>/i);

    if (titleMatch) {
      const cleanTitle = titleMatch[1]
        .replace(/<[^>]+>/g, '')
        .replace(/&amp;/g, '&')
        .replace(/&quot;/g, '"')
        .replace(/&#039;/g, "'")
        .replace(/\s+/g, ' ')
        .trim();

      const cleanSnippet = descMatch
        ? descMatch[1]
            .replace(/<[^>]+>/g, '')
            .replace(/&amp;/g, '&')
            .replace(/&quot;/g, '"')
            .replace(/&#039;/g, "'")
            .replace(/\s+/g, ' ')
            .trim()
            .slice(0, 240)
        : '';

      if (cleanTitle.length > 5) {
        items.push({
          source: sourceName,
          title: cleanTitle,
          snippet: cleanSnippet,
          category
        });
      }
    }
  }
  return items;
}

// 1. Web Crawler: Scrapes live global news, RSS feeds, tech updates & Wikipedia
export async function crawlGlobalDailyNews(): Promise<{ items: ScrapedItem[]; rawText: string }> {
  const allItems: ScrapedItem[] = [];

  const crawlTasks: Promise<ScrapedItem[]>[] = [
    // World Geopolitics
    fetchSafe('http://feeds.bbci.co.uk/news/world/rss.xml').then(xml => parseXmlItems(xml, 'BBC World', 'GEOPOLITICS', 10)),
    fetchSafe('https://feeds.npr.org/1004/rss.xml').then(xml => parseXmlItems(xml, 'NPR World', 'GEOPOLITICS', 8)),
    fetchSafe('https://www.aljazeera.com/xml/rss/all.xml').then(xml => parseXmlItems(xml, 'Al Jazeera', 'GEOPOLITICS', 8)),
    fetchSafe('https://news.google.com/rss/topics/CAAqJggKIiBDQkFTRWdvSUwyMHZNRGx1YlY4U0FtVnVHZ0pWVXlnQVAB?hl=en-US&gl=US&ceid=US:en')
      .then(xml => parseXmlItems(xml, 'Google News World Wire', 'GEOPOLITICS', 10)),

    // Tech & Artificial Intelligence
    fetchSafe('https://techcrunch.com/feed/').then(xml => parseXmlItems(xml, 'TechCrunch', 'TECH & AI', 8)),
    fetchSafe('https://www.theverge.com/rss/index.xml').then(xml => parseXmlItems(xml, 'The Verge', 'TECH & AI', 8)),
    fetchSafe('https://feeds.arstechnica.com/arstechnica/index').then(xml => parseXmlItems(xml, 'Ars Technica', 'TECH & AI', 8)),
    fetchSafe('https://news.google.com/rss/topics/CAAqJggKIiBDQkFTRWdvSUwyMHZNRGRqTVhZU0FtVnVHZ0pWVXlnQVAB?hl=en-US&gl=US&ceid=US:en')
      .then(xml => parseXmlItems(xml, 'Google News Tech Wire', 'TECH & AI', 10)),

    // Science & Space
    fetchSafe('https://www.sciencedaily.com/rss/top/science.xml').then(xml => parseXmlItems(xml, 'ScienceDaily', 'SCIENCE & SPACE', 6)),
    fetchSafe('https://www.nasa.gov/news-release/feed/').then(xml => parseXmlItems(xml, 'NASA News', 'SCIENCE & SPACE', 6)),

    // Hacker News Top Stories
    (async () => {
      try {
        const topIdsRaw = await fetchSafe('https://hacker-news.firebaseio.com/v0/topstories.json', {}, 3000);
        if (!topIdsRaw) return [];
        const topIds = (JSON.parse(topIdsRaw) as number[]).slice(0, 10);
        const hnTasks = topIds.map(async id => {
          const itemRaw = await fetchSafe(`https://hacker-news.firebaseio.com/v0/item/${id}.json`, {}, 2000);
          if (!itemRaw) return null;
          const data = JSON.parse(itemRaw);
          if (data && data.title) {
            return {
              source: 'Hacker News',
              title: data.title,
              snippet: `Points: ${data.score || 0}, Comments: ${data.descendants || 0}`,
              category: 'TECH & AI'
            } as ScrapedItem;
          }
          return null;
        });
        const results = await Promise.all(hnTasks);
        return results.filter(Boolean) as ScrapedItem[];
      } catch (e) {
        return [];
      }
    })(),

    // Wikipedia Current Events & Knowledge
    (async () => {
      try {
        const wikiRaw = await fetchSafe('https://en.wikipedia.org/w/api.php?action=query&list=search&srsearch=current+events+world+news&utf8=&format=json', {}, 3000);
        if (!wikiRaw) return [];
        const wikiData = JSON.parse(wikiRaw);
        const searchItems = wikiData?.query?.search || [];
        return searchItems.slice(0, 6).map((item: any) => ({
          source: 'Wikipedia',
          title: item.title,
          snippet: (item.snippet || '').replace(/<[^>]+>/g, '').trim(),
          category: 'WORLD FACTS'
        })) as ScrapedItem[];
      } catch (e) {
        return [];
      }
    })()
  ];

  const settled = await Promise.allSettled(crawlTasks);
  for (const s of settled) {
    if (s.status === 'fulfilled' && Array.isArray(s.value)) {
      allItems.push(...s.value);
    }
  }

  // Format into compact raw text digest for the LLM
  const rawLines = allItems.map(item => `[${item.source} | ${item.category}]: ${item.title} -- ${item.snippet}`);
  const rawText = rawLines.join('\n');

  return { items: allItems, rawText };
}

// 2. Cloud LLM (Groq / Gemini / Workers AI): Distills into ultra-dense 50-bullet Knowledge Digest
export async function distillDailyDigest(env: Env, rawArticles: string): Promise<string> {
  const todayStr = new Date().toISOString().split('T')[0];

  const systemPrompt = `You are the Sentinel Intelligence Synthesizer. Today's date is ${todayStr}.
You distill thousands of live news feeds, tech articles, and Wikipedia updates into an ultra-dense, exactly 50-bullet "Daily World Knowledge Digest".

Strict Output Requirements:
1. Output exactly 50 bullet points. No more, no less.
2. Every bullet MUST start with a dash followed by a capitalized category in brackets:
   - [GEOPOLITICS] ...
   - [AI & TECH] ...
   - [SCIENCE & SPACE] ...
   - [BUSINESS & FINANCE] ...
   - [GLOBAL SECURITY] ...
   - [ENERGY & ENVIRONMENT] ...
3. Each bullet must be 1 dense, fact-filled sentence stating exact names, countries, organizations, metrics, and confirmed real-world developments.
4. NO conversational introductions, NO greetings, NO conclusions, NO markdown bold (**), NO numbered lists (1., 2.).
5. Start immediately with the first bullet point: "- [GEOPOLITICS] ..."`;

  const userPrompt = `Distill these ${rawArticles.split('\n').length} scraped live global updates into the 50-bullet Daily World Knowledge Digest for ${todayStr}:\n\n${rawArticles.slice(0, 18000)}`;

  // Priority 1: Groq LPU (llama-3.3-70b-versatile) - Ultra-fast & high quality
  if (env.GROQ_API_KEY) {
    try {
      const res = await fetch('https://api.groq.com/openai/v1/chat/completions', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${env.GROQ_API_KEY}`
        },
        body: JSON.stringify({
          model: 'llama-3.3-70b-versatile',
          messages: [
            { role: 'system', content: systemPrompt },
            { role: 'user', content: userPrompt }
          ],
          max_tokens: 3500,
          temperature: 0.3
        })
      });

      if (res.ok) {
        const data = await res.json() as any;
        const text = data.choices?.[0]?.message?.content?.trim();
        if (text && text.includes('- [')) {
          return text;
        }
      }
    } catch (e) {
      console.warn('[GROQ DISTILL ERROR]:', e);
    }
  }

  // Priority 2: Gemini 1.5 Flash
  if (env.GEMINI_API_KEY) {
    try {
      const res = await fetch(`https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=${env.GEMINI_API_KEY}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          contents: [{
            parts: [{ text: `${systemPrompt}\n\n${userPrompt}` }]
          }],
          generationConfig: {
            maxOutputTokens: 3500,
            temperature: 0.3
          }
        })
      });

      if (res.ok) {
        const data = await res.json() as any;
        const text = data.candidates?.[0]?.content?.parts?.[0]?.text?.trim();
        if (text && text.includes('- [')) {
          return text;
        }
      }
    } catch (e) {
      console.warn('[GEMINI DISTILL ERROR]:', e);
    }
  }

  // Priority 3: Cloudflare Workers AI
  if (env.AI) {
    try {
      const aiRes = await env.AI.run('@cf/meta/llama-3.1-8b-instruct', {
        messages: [
          { role: 'system', content: systemPrompt },
          { role: 'user', content: userPrompt }
        ],
        max_tokens: 3500,
        temperature: 0.3
      });
      const text = aiRes.response?.trim();
      if (text && text.includes('- [')) {
        return text;
      }
    } catch (e) {
      console.warn('[WORKERS AI DISTILL ERROR]:', e);
    }
  }

  // Fallback if LLM unavailable: direct structured synthesis from scraped items
  return rawArticles
    .split('\n')
    .slice(0, 50)
    .map(line => `- [WORLD NEWS] ${line.replace(/^\[[^\]]+\]:\s*/, '')}`)
    .join('\n');
}

// Supabase PostgREST helper
async function supabaseInsert(env: Env, table: string, payload: any): Promise<any> {
  const supabaseUrl = env.SUPABASE_URL || (env.SHADOW_MEMORY ? await env.SHADOW_MEMORY.get('SUPABASE_URL') : null);
  const supabaseKey = env.SUPABASE_SERVICE_KEY || env.SUPABASE_ANON_KEY || (env.SHADOW_MEMORY ? await env.SHADOW_MEMORY.get('SUPABASE_KEY') : null);

  if (!supabaseUrl || !supabaseKey) {
    return null;
  }

  const cleanUrl = supabaseUrl.replace(/\/+$/, '') + '/rest/v1/' + table;
  try {
    const res = await fetch(cleanUrl, {
      method: 'POST',
      headers: {
        'apikey': supabaseKey,
        'Authorization': `Bearer ${supabaseKey}`,
        'Content-Type': 'application/json',
        'Prefer': 'resolution=merge-duplicates,return=representation'
      },
      body: JSON.stringify(Array.isArray(payload) ? payload : [payload])
    });
    if (!res.ok) {
      console.warn('[SUPABASE INSERT ERROR]:', res.status, await res.text());
      return null;
    }
    return await res.json();
  } catch (e) {
    console.warn('[SUPABASE INSERT EXCEPTION]:', e);
    return null;
  }
}

// 3. Save to Cloudflare KV & Supabase PostgreSQL
export async function saveDailyDigest(env: Env, digest: DailyDigestRecord): Promise<void> {
  // 3a. Cloudflare KV Store
  if (env.SHADOW_MEMORY) {
    // Current latest digest for immediate fetch by Galaxy S9+
    await env.SHADOW_MEMORY.put('DAILY_SENTINEL_DIGEST', JSON.stringify(digest));
    // Date-indexed archive in KV
    await env.SHADOW_MEMORY.put(`DAILY_SENTINEL_DIGEST_${digest.digestDate}`, JSON.stringify(digest));

    // Also pin top 25 bullets into pinned_knowledge_base for conversational grounding
    const topBullets = digest.bullets.slice(0, 25).join('\n');
    await env.SHADOW_MEMORY.put('pinned_knowledge_base', `[DAILY WORLD KNOWLEDGE DIGEST - ${digest.digestDate}]:\n${topBullets}`);
  }

  // 3b. Supabase PostgreSQL (Prevents KV storage depletion & maintains permanent historical archive)
  try {
    await supabaseInsert(env, 'daily_sentinel_digests', {
      digest_date: digest.digestDate,
      bullet_count: digest.bulletCount,
      categories: digest.categories,
      bullets: digest.bullets,
      raw_digest_text: digest.rawDigestText,
      source_count: digest.sourceCount
    });

    await supabaseInsert(env, 'daily_learned_insights', {
      insight_type: 'DAILY_DIGEST',
      summary: `Synthesized 50-bullet world knowledge digest from ${digest.sourceCount} live global sources on ${digest.digestDate}.`,
      confidence: 0.98,
      learned_date: digest.digestDate
    });
  } catch (e) {
    console.warn('[SUPABASE PERSIST ERROR]:', e);
  }
}

// Full Execution Pipeline (Scheduled at 04:00 AM UTC or on-demand)
export async function executeDailyDigestPipeline(env: Env): Promise<DailyDigestRecord> {
  const digestDate = new Date().toISOString().split('T')[0];
  console.log(`[DAILY DIGEST PIPELINE]: Starting crawling at ${new Date().toISOString()}...`);

  // 1. Crawl
  const { items, rawText } = await crawlGlobalDailyNews();
  console.log(`[DAILY DIGEST PIPELINE]: Scraped ${items.length} live articles.`);

  // 2. Distill with Cloud LLM
  const rawDigestText = await distillDailyDigest(env, rawText);
  console.log(`[DAILY DIGEST PIPELINE]: Distilled digest received (${rawDigestText.length} chars).`);

  // Parse lines into clean bullets
  const lines = rawDigestText.split('\n').map(l => l.trim()).filter(l => l.startsWith('- '));
  const bullets = lines.length >= 20 ? lines : rawDigestText.split('\n').filter(Boolean);

  // Group by category
  const categories: Record<string, string[]> = {};
  for (const b of bullets) {
    const catMatch = b.match(/^-\s*\[(.*?)\]/);
    const cat = catMatch ? catMatch[1].trim() : 'GENERAL';
    if (!categories[cat]) categories[cat] = [];
    categories[cat].push(b);
  }

  const record: DailyDigestRecord = {
    digestDate,
    generatedAt: Date.now(),
    bulletCount: bullets.length,
    categories,
    bullets,
    rawDigestText,
    sourceCount: items.length
  };

  // 3. Save to KV and PostgreSQL
  await saveDailyDigest(env, record);
  console.log(`[DAILY DIGEST PIPELINE]: Successfully saved ${bullets.length}-bullet digest to KV and Supabase!`);

  return record;
}

// Retrieve Latest Digest (checks KV first, then Supabase)
export async function getLatestDailyDigest(env: Env): Promise<DailyDigestRecord | null> {
  if (env.SHADOW_MEMORY) {
    const kvData = await env.SHADOW_MEMORY.get('DAILY_SENTINEL_DIGEST');
    if (kvData) {
      try {
        return JSON.parse(kvData) as DailyDigestRecord;
      } catch (e) {}
    }
  }

  // Fallback to Supabase
  const supabaseUrl = env.SUPABASE_URL || (env.SHADOW_MEMORY ? await env.SHADOW_MEMORY.get('SUPABASE_URL') : null);
  const supabaseKey = env.SUPABASE_SERVICE_KEY || env.SUPABASE_ANON_KEY || (env.SHADOW_MEMORY ? await env.SHADOW_MEMORY.get('SUPABASE_KEY') : null);

  if (supabaseUrl && supabaseKey) {
    try {
      const cleanUrl = supabaseUrl.replace(/\/+$/, '') + '/rest/v1/daily_sentinel_digests?order=digest_date.desc&limit=1';
      const res = await fetch(cleanUrl, {
        headers: {
          'apikey': supabaseKey,
          'Authorization': `Bearer ${supabaseKey}`
        }
      });
      if (res.ok) {
        const rows = await res.json() as any[];
        if (rows.length > 0) {
          const row = rows[0];
          return {
            digestDate: row.digest_date,
            generatedAt: new Date(row.created_at).getTime(),
            bulletCount: row.bullet_count || row.bullets?.length || 50,
            categories: row.categories || {},
            bullets: row.bullets || [],
            rawDigestText: row.raw_digest_text || '',
            sourceCount: row.source_count || 0
          };
        }
      }
    } catch (e) {}
  }

  return null;
}
