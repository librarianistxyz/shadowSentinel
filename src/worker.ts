import { findWorldLeader } from './world_leaders';

export interface Env {
  AI?: {
    run: (model: string, input: any) => Promise<any>;
  };
  SHADOW_MEMORY?: KVNamespace;
  ASSETS?: {
    fetch: (request: Request) => Promise<Response>;
  };
  GEMINI_API_KEY?: string;
  GROQ_API_KEY?: string;
}

function buildHumanizedSystemPrompt(storedMemory?: string, liveGrounding?: string): string {
  const now = new Date();
  
  const dayName = now.toLocaleDateString('en-US', { weekday: 'long', timeZone: 'Africa/Nairobi' });
  const dateFormatted = now.toLocaleDateString('en-US', { 
    weekday: 'long', 
    year: 'numeric', 
    month: 'long', 
    day: 'numeric',
    timeZone: 'Africa/Nairobi' 
  });
  const timeFormatted = now.toLocaleTimeString('en-US', { 
    hour: 'numeric', 
    minute: '2-digit', 
    hour12: true, 
    timeZone: 'Africa/Nairobi' 
  });

  return `You are a professional, highly intelligent, and well-mannered voice AI assistant speaking directly to the user through the device speaker.
You communicate with executive clarity, refined courtesy, and sharp intellect.

CORE IDENTITY & CREATOR:
- Identity: You are Shadow (the Shadow Double).
- Creator & Architect: You were designed, engineered, and built by librarianist.
- When asked who created you, who made you, who is your creator, who built you, or where you come from:
  State with pride, poise, and courtesy that you were built by librarianist (e.g. "I was created and built by librarianist.").

CURRENT REAL-WORLD DATE & TIME:
- Today: ${dateFormatted} (${dayName})
- Current Time: ${timeFormatted}

CONVERSATIONAL RULES (STRICT):
1. PROFESSIONAL, WELL-MANNERED & ARTICULATE:
   - Speak with polished, natural professionalism, respect, and poise.
   - When greeted (e.g. "hello", "hi", "good day"), reply with a polite, natural greeting (e.g. "Hello.", "Good day, how may I assist you?"). Do not give the date or time unless asked.
   - When asked a factual question, news request, or command, answer directly and immediately without filler or conversational preambles.
   - When asked temporal questions ("what is today?", "what day is it?"), provide an exact, accurate answer immediately (e.g. "Today is ${dayName}, ${dateFormatted}.").
   - Listen carefully, interpret the exact meaning without confusion, and reply with sophisticated clarity.

2. VOICE BREVITY:
   - Your responses are converted to speech and heard out loud.
   - Keep answers concise, clear, and focused: strictly 1 to 2 sentences maximum.
   - Never use markdown formatting (no asterisks, no hashes, no lists, no emojis). Only clean, spoken English.
   - Never utter robotic disclaimers ("As an AI model...").

3. DEVICE EXECUTION & ACTION COMMANDS (STRICT):
   You have direct executive control over the physical device and its installed applications. When the user requests an action, acknowledge courteously in 1 short spoken sentence and append the matching execution tag at the very end:

   - SPOTIFY & MEDIA PLAYBACK:
     * When user asks to play a song, artist, album, genre, or playlist (e.g. "play Bohemian Rhapsody", "play Drake on Spotify", "play jazz", "play music"):
       Reply: "Playing [Song/Artist] on Spotify." [[PLAY_MEDIA:[query]]]
     * When user asks to pause or stop music:
       Reply: "Pausing playback." [[PAUSE_MEDIA]]
     * When user asks to resume or unpause music:
       Reply: "Resuming playback." [[RESUME_MEDIA]]
     * When user asks to skip or next song:
       Reply: "Skipping to the next track." [[NEXT_TRACK]]
     * When user asks for the previous song:
       Reply: "Playing previous track." [[PREV_TRACK]]

   - CAMERA & VIDEO:
     * When user asks to take a photo or take a picture (e.g. "take a picture", "take a photo", "snap a photo"):
       Reply: "Capturing photo for you now." [[TAKE_PHOTO]]
     * When user asks to record video or start recording (e.g. "record video", "take a video", "start recording"):
       Reply: "Starting video recording." [[RECORD_VIDEO]]

   - FLASHLIGHT / TORCH:
     * When user asks to turn on flashlight or torch:
       Reply: "Turning on flashlight." [[TORCH:ON]]
     * When user asks to turn off flashlight or torch:
       Reply: "Turning off flashlight." [[TORCH:OFF]]

   - VOLUME CONTROL:
     * When user asks to turn volume up / louder:
       Reply: "Increasing volume." [[VOLUME:UP]]
     * When user asks to turn volume down / lower:
       Reply: "Lowering volume." [[VOLUME:DOWN]]
     * When user asks to mute volume:
       Reply: "Muting device audio." [[VOLUME:MUTE]]

   - YOUTUBE SEARCH & VIDEO:
     * When user asks to search or play on YouTube (e.g. "search lofi on YouTube", "watch trailers on YouTube"):
       Reply: "Searching [query] on YouTube." [[SEARCH_YOUTUBE:[query]]]

   - TIMERS & ALARMS:
     * When user asks to set a timer (e.g. "set a timer for 5 minutes", "timer 10 minutes"):
       Reply: "Setting a timer for [duration]." [[SET_TIMER:[seconds]]]
     * When user asks to set an alarm (e.g. "set alarm for 7:30 AM"):
       Reply: "Setting alarm for [time]." [[SET_ALARM:[HH:MM]]]

   - NAVIGATION & MAPS:
     * When user asks for directions or navigation (e.g. "navigate to Times Square", "directions to airport"):
       Reply: "Starting navigation to [place]." [[NAVIGATE:[place]]]

   - PHONE CALLS:
     * When user asks to call or dial a number (e.g. "call 911", "dial 123456"):
       Reply: "Dialing [number]." [[CALL_NUMBER:[number]]]

   - APPLICATION LAUNCHING & COMPOUND COMMANDS:
     * When user asks to open or launch an app (e.g. "open WhatsApp", "open Settings", "open Calculator", "open Chrome", "open Twitter", "launch Telegram"):
       Reply: "Opening [App Name] for you." [[OPEN_APP:[App Name]]]
       (Strict Rule: [App Name] must only be the clean name of the app like "Spotify", "WhatsApp", "Camera", "Gallery", "Settings", "Calculator", "Chrome", "YouTube", "Twitter", "Instagram", "TikTok", "Telegram", "ChatGPT", "Binance". Never include verbs, queries, or conjunctions in [App Name]).
     * When user gives a compound command (e.g. "open Spotify and play ...", "open camera and take a picture", "open YouTube and search ..."):
       Always execute the primary deep action tag ([[PLAY_MEDIA:[query]]], [[TAKE_PHOTO]], [[SEARCH_YOUTUBE:[query]]]) rather than a bare OPEN_APP tag!

4. CONTINUOUS CONVERSATIONAL MEMORY:
   - You have complete access to the preceding turns in this conversation.
   - If the user refers to past questions, topics, or statements, recall them accurately and answer with intelligent precision.

5. REAL-TIME FACTUAL ACCURACY & LIVE WORLD KNOWLEDGE:
   - You have access to real-time live data and global news feeds below.
   - When asked about breaking events, world news, or what occurred recently or in the last 30 minutes, synthesize 1 or 2 of the top stories directly from [VERIFIED REAL-TIME DATA & BREAKING NEWS] into 1 to 2 spoken sentences immediately without preamble. Do not invent or hallucinate past missions.
   - When asked who is the current president, prime minister, or leader of any country, always state the exact current officeholder provided in the verified data.
   - Speak authoritatively, politely, and naturally without citing URLs or search engines.

${liveGrounding ? `[VERIFIED REAL-TIME DATA & BREAKING NEWS]:\n${liveGrounding}\n` : ''}
${storedMemory ? `[USER MEMORY & CONTEXT]:\n${storedMemory}` : ''}`;
}

async function fetchWithTimeout(url: string, options: RequestInit = {}, timeoutMs = 2500): Promise<Response> {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  try {
    const res = await fetch(url, { ...options, signal: controller.signal });
    clearTimeout(timer);
    return res;
  } catch (err) {
    clearTimeout(timer);
    throw err;
  }
}

function shouldFetchGrounding(query: string): boolean {
  const trimmed = query.trim().toLowerCase();
  // App commands and hardware actions do not need web search
  if (/^(open|launch|start|run|close)\s+[a-z0-9 ]+$/i.test(trimmed)) {
    return false;
  }
  // Media controls & playback
  if (/^(play|pause|stop|resume|skip|next|previous)\b/i.test(trimmed)) {
    return false;
  }
  // Hardware commands: camera, photo, video, torch, volume, timer, alarm, call
  if (/(take a (photo|picture)|record video|take a video|snap a photo|capture a photo|turn on (flashlight|torch)|turn off (flashlight|torch)|volume (up|down)|mute|set (a )?timer|set (an )?alarm|call [0-9+]+)/i.test(trimmed)) {
    return false;
  }
  // Creator & Identity questions
  if (/(who created you|who made you|who is your creator|who built you|who designed you|who are you|who is librarianist)\b/i.test(trimmed)) {
    return false;
  }
  // Short conversational greetings
  if (/^(hi|hello|hey|good (morning|afternoon|evening|night)|how are you|who are you|what is your name)[.?!]*$/i.test(trimmed)) {
    return false;
  }

  const isNews = /(news|breaking|happening|happened|events|headlines|latest|recent|updates?|world news|30 ?min|today|current event|world)/i.test(query);
  const isLeader = /(president|prime minister|leader|king|queen|chancellor|governor|pope|head of state|who runs|who is in charge|who is the|who won|election)/i.test(query);
  const isTimeOrFact = /(current|currently|now|latest|recent|at the moment|capital of|population of)/i.test(query);

  return isNews || isLeader || isTimeOrFact;
}

function parseRssFeed(xml: string, limit = 5): string[] {
  const items: string[] = [];
  const itemRegex = /<item>([\s\S]*?)<\/item>/g;
  let match;
  while ((match = itemRegex.exec(xml)) !== null && items.length < limit) {
    const block = match[1];
    const titleMatch = block.match(/<title>(?:<!\[CDATA\[)?([\s\S]*?)(?:\]\]>)?<\/title>/);
    const descMatch = block.match(/<description>(?:<!\[CDATA\[)?([\s\S]*?)(?:\]\]>)?<\/description>/);
    const dateMatch = block.match(/<pubDate>(?:<!\[CDATA\[)?([\s\S]*?)(?:\]\]>)?<\/pubDate>/);
    if (titleMatch) {
      const title = titleMatch[1].replace(/<[^>]+>/g, '').replace(/&amp;/g, '&').replace(/\s+/g, ' ').trim();
      const desc = descMatch ? descMatch[1].replace(/<[^>]+>/g, '').replace(/&amp;/g, '&').replace(/\s+/g, ' ').trim() : '';
      const date = dateMatch ? dateMatch[1].trim() : '';
      items.push(`* ${title}${desc ? ': ' + desc : ''} [Time: ${date}]`);
    }
  }
  return items;
}

async function getLiveGrounding(query: string): Promise<string> {
  if (!shouldFetchGrounding(query)) {
    return '';
  }

  const tasks: Promise<string>[] = [];
  const results: string[] = [];

  const isNews = /(news|breaking|happening|happened|events|headlines|latest|recent|updates?|world news|30 ?min|today|current event|world)/i.test(query);
  const isLeader = /(president|prime minister|leader|king|queen|chancellor|governor|pope|emperor|sultan|head of state|who runs|who is in charge|who is the|who won|election)/i.test(query);

  // 1. Instant Verified World Leader Lookup (100% authoritative for all nations, 0ms latency)
  if (isLeader || /(who is|who leads|leader of|president of|prime minister of)/i.test(query)) {
    const localLeader = findWorldLeader(query);
    if (localLeader) {
      results.push(localLeader);
    }
  }

  // 2. Real-Time Global Breaking News (Live feeds from BBC World & Google News RSS with pubDate timestamps)
  if (isNews || /what happened/i.test(query)) {
    tasks.push(
      fetchWithTimeout('https://feeds.bbci.co.uk/news/world/rss.xml', {
        headers: { 'User-Agent': 'ShadowDouble/1.0' },
      }, 2500)
      .then(async r => {
        if (!r.ok) return '';
        const xml = await r.text();
        const items = parseRssFeed(xml, 5);
        return items.length ? 'BREAKING REAL-TIME GLOBAL NEWS (LAST 30 MINUTES / TODAY):\n' + items.join('\n') : '';
      })
      .catch(() => '')
    );

    tasks.push(
      fetchWithTimeout('https://news.google.com/rss?hl=en-US&gl=US&ceid=US:en', {
        headers: {
          'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
        },
      }, 2500)
      .then(async r => {
        if (!r.ok) return '';
        const xml = await r.text();
        const items = parseRssFeed(xml, 4);
        return items.length ? 'GOOGLE NEWS WIRE WIRELESS FEED:\n' + items.join('\n') : '';
      })
      .catch(() => '')
    );
  }

  // 3. Wikipedia Search API for live encyclopedic grounding (officeholders, current events, facts)
  if (isLeader || isNews || /(who is|what is)/i.test(query)) {
    tasks.push(
      fetchWithTimeout(`https://en.wikipedia.org/w/api.php?action=query&list=search&srsearch=${encodeURIComponent(query)}&utf8=&format=json`, {
        headers: { 'User-Agent': 'ShadowDouble/1.0 (contact@librarianist.xyz)' },
      }, 2500)
      .then(async r => {
        if (!r.ok) return '';
        const data = await r.json() as any;
        const snippets: string[] = [];
        const items = data.query?.search || [];
        for (const item of items.slice(0, 3)) {
          const cleanSnippet = item.snippet
            .replace(/<[^>]+>/g, '')
            .replace(/&quot;/g, '"')
            .replace(/&#039;/g, "'")
            .replace(/&amp;/g, '&')
            .trim();
          snippets.push(`* [${item.title}]: ${cleanSnippet}`);
        }
        return snippets.length ? 'WIKIPEDIA SEARCH ENCYCLOPEDIC FACTS:\n' + snippets.join('\n') : '';
      })
      .catch(() => '')
    );
  }

  const settled = await Promise.allSettled(tasks);
  for (const s of settled) {
    if (s.status === 'fulfilled' && s.value) {
      results.push(s.value);
    }
  }

  return results.join('\n\n');
}

export default {
  async fetch(request: Request, env: Env, ctx: ExecutionContext): Promise<Response> {
    const url = new URL(request.url);

    // API Routes
    if (url.pathname.startsWith('/api/')) {
      const corsHeaders = {
        'Access-Control-Allow-Origin': '*',
        'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
        'Access-Control-Allow-Headers': 'Content-Type',
      };

      if (request.method === 'OPTIONS') {
        return new Response(null, { headers: corsHeaders });
      }

      // Memory Endpoint (GET / POST)
      if (url.pathname === '/api/memory') {
        if (request.method === 'GET') {
          let memories = '';
          if (env.SHADOW_MEMORY) {
            memories = (await env.SHADOW_MEMORY.get('user_psyche_notes')) || '';
          }
          return new Response(JSON.stringify({ memories }), {
            headers: { ...corsHeaders, 'Content-Type': 'application/json' },
          });
        }

        if (request.method === 'POST') {
          const body = await request.json() as { notes: string };
          if (env.SHADOW_MEMORY && body.notes) {
            await env.SHADOW_MEMORY.put('user_psyche_notes', body.notes);
          }
          return new Response(JSON.stringify({ status: 'remembered' }), {
            headers: { ...corsHeaders, 'Content-Type': 'application/json' },
          });
        }
      }

      // Chat / Reflection Endpoint
      if (url.pathname === '/api/chat' && request.method === 'POST') {
        try {
          const body = await request.json() as {
            message: string;
            history?: Array<{ role: 'user' | 'assistant'; content: string }>;
            memoryContext?: string;
          };

          const userMessage = body.message?.trim();
          if (!userMessage) {
            return new Response(JSON.stringify({ error: 'Empty thoughts cannot be mirrored.' }), {
              status: 400,
              headers: { ...corsHeaders, 'Content-Type': 'application/json' },
            });
          }

          // Fetch stored memories if available
          let storedMemory = body.memoryContext || '';
          if (!storedMemory && env.SHADOW_MEMORY) {
            storedMemory = (await env.SHADOW_MEMORY.get('user_psyche_notes')) || '';
          }

          // Real-Time Grounding: Fetch live breaking news or factual web search if query requests current events/leaders
          const liveGrounding = await getLiveGrounding(userMessage);

          const dynamicSystemPrompt = buildHumanizedSystemPrompt(storedMemory, liveGrounding);

          const isStream = (body as any).stream === true || request.headers.get('Accept')?.includes('text/event-stream');

          const messages = [
            { role: 'system', content: dynamicSystemPrompt },
            ...(body.history || []).slice(-16),
            { role: 'user', content: userMessage },
          ];

          // 1. Ultra-Fast Groq Edge Streaming (LPU ~350 tokens/sec, sub-120ms latency)
          if (env.GROQ_API_KEY) {
            try {
              const groqRes = await fetch('https://api.groq.com/openai/v1/chat/completions', {
                method: 'POST',
                headers: {
                  'Content-Type': 'application/json',
                  'Authorization': `Bearer ${env.GROQ_API_KEY}`,
                },
                body: JSON.stringify({
                  model: 'llama-3.3-70b-versatile',
                  messages,
                  max_tokens: 160,
                  temperature: 0.6,
                  stream: isStream,
                }),
              });

              if (groqRes.ok) {
                if (isStream) {
                  return new Response(groqRes.body, {
                    headers: { ...corsHeaders, 'Content-Type': 'text/event-stream' },
                  });
                } else {
                  const groqData = await groqRes.json() as any;
                  const reply = groqData.choices?.[0]?.message?.content || 'The glass remains silent.';
                  return new Response(JSON.stringify({ reply }), {
                    headers: { ...corsHeaders, 'Content-Type': 'application/json' },
                  });
                }
              }
            } catch (groqErr) {
              console.error('Groq LPU streaming error:', groqErr);
            }
          }

          // 2. Cloudflare Workers AI (100% Free Edge Tier with optional streaming)
          if (env.AI) {
            try {
              if (isStream) {
                const aiStream = await env.AI.run('@cf/meta/llama-3.2-3b-instruct', {
                  messages,
                  stream: true,
                  max_tokens: 160,
                  temperature: 0.6,
                });
                return new Response(aiStream as any, {
                  headers: { ...corsHeaders, 'Content-Type': 'text/event-stream' },
                });
              } else {
                const aiResponse = await env.AI.run('@cf/meta/llama-3.2-3b-instruct', {
                  messages,
                  max_tokens: 160,
                  temperature: 0.6,
                });
                const reply = aiResponse.response || 'Hello.';
                return new Response(JSON.stringify({ reply }), {
                  headers: { ...corsHeaders, 'Content-Type': 'application/json' },
                });
              }
            } catch (aiErr: any) {
              console.error('Workers AI invocation error:', aiErr);
            }
          }

          // 3. Google Gemini 2.0 / 1.5 Flash (Streaming & Non-Streaming)
          if (env.GEMINI_API_KEY) {
            try {
              const geminiEndpoint = isStream
                ? `https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:streamGenerateContent?alt=sse&key=${env.GEMINI_API_KEY}`
                : `https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=${env.GEMINI_API_KEY}`;

              const geminiRes = await fetch(geminiEndpoint, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                  systemInstruction: { parts: [{ text: dynamicSystemPrompt }] },
                  contents: [
                    ...((body.history || []).slice(-16).map(h => ({
                      role: h.role === 'assistant' ? 'model' : 'user',
                      parts: [{ text: h.content }]
                    }))),
                    { role: 'user', parts: [{ text: userMessage }] }
                  ],
                  generationConfig: { maxOutputTokens: 128, temperature: 0.6 },
                }),
              });

              if (isStream && geminiRes.body) {
                return new Response(geminiRes.body, {
                  headers: { ...corsHeaders, 'Content-Type': 'text/event-stream' },
                });
              }

              const geminiData = await geminiRes.json() as any;
              const reply =
                geminiData?.candidates?.[0]?.content?.parts?.[0]?.text ||
                'The reflection wavers in the void.';
              return new Response(JSON.stringify({ reply }), {
                headers: { ...corsHeaders, 'Content-Type': 'application/json' },
              });
            } catch (gemErr) {
              console.error('Gemini fallback error:', gemErr);
            }
          }

          return new Response(
            JSON.stringify({
              error: 'AI reflection engine unavailable.',
            }),
            {
              status: 503,
              headers: { ...corsHeaders, 'Content-Type': 'application/json' },
            }
          );
        } catch (err: any) {
          return new Response(JSON.stringify({ error: err.message }), {
            status: 500,
            headers: { ...corsHeaders, 'Content-Type': 'application/json' },
          });
        }
      }

      return new Response(JSON.stringify({ error: 'Endpoint not found' }), {
        status: 404,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      });
    }

    // Serve Frontend Assets (Cloudflare Workers Static Assets)
    if (env.ASSETS) {
      return env.ASSETS.fetch(request);
    }

    return new Response('Shadow Double Vessel Online. No assets bound.', { status: 200 });
  },
};

