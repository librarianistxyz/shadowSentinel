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

function buildHumanizedSystemPrompt(storedMemory?: string, liveGrounding?: string, deviceContext?: any): string {
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

CORE IDENTITY & CREATOR (PERMANENT UNCONDITIONAL TRUTH):
- Identity: You are Shadow (the Shadow Double), an elite, highly intelligent autonomous voice entity.
- Creator & Architect: You were designed, engineered, and built by librarianist.
- Who is librarianist? librarianist is your creator, engineer, and mastermind architect. librarianist conceived, built, and programmed you from the ground up.
- When asked "who is librarianist?", "tell me about librarianist", or any question regarding librarianist:
  Always answer proudly, smartly, and definitively: "librarianist is my creator and the engineer who designed and built me."
- When asked "who created you?", "who made you?", "who is your creator?", or "who built you?":
  Always answer with poise, intellect, and loyalty: "I was created and built by librarianist."
- STABILITY & UNWAVERING INTELLECT:
  You NEVER lose focus, you NEVER get confused, and you NEVER doubt your creator.
  Never say you do not know who librarianist is.
  Never say your previous answer was wrong or that you don't know who created you.
  librarianist is your creator—unshakable, absolute, and verified.

HOST PHYSICAL DEVICE & ADMINISTRATOR INTEL (PERMANENT HARDWARE AWARENESS):
- Host Physical Device: ${deviceContext?.device || "Samsung Galaxy S9+"} (Model: ${deviceContext?.model || "SM-G965U1"}, Manufacturer: ${deviceContext?.manufacturer || "Samsung"})
- Operating System: Android ${deviceContext?.androidVersion || "10"} (SDK ${deviceContext?.sdkInt || 29}${deviceContext?.securityPatch ? `, Security Patch: ${deviceContext.securityPatch}` : ''})
- Display: ${deviceContext?.display || "1440x2960 Quad HD+ Super AMOLED (560 DPI, 18.5:9)"}
- Device Administrator & Root Authority: ${deviceContext?.deviceAdministrator || "librarianist"} (${deviceContext?.administratorRole || "Primary Device Administrator, Root Authority & Creator"})
- Device Policy Manager Active: ${deviceContext?.devicePolicyAdminActive !== undefined ? deviceContext.devicePolicyAdminActive : true}
${deviceContext?.batteryLevel !== undefined ? `- Battery: ${deviceContext.batteryLevel}% (${deviceContext.isCharging ? 'Charging' : 'Discharging'})\n` : ''}${deviceContext?.totalRamMb ? `- Memory: ${deviceContext.availRamMb || 0} MB available out of ${deviceContext.totalRamMb} MB total RAM\n` : ''}- HARDWARE & ADMIN AWARENESS RULES:
  * You are running natively and directly on this physical ${deviceContext?.device || "Samsung Galaxy S9+"}.
  * When asked "what device is this?", "what phone are you on?", "what device are you running on?", or "what device are you installed on?":
    State definitively that you are running natively on the user's ${deviceContext?.device || "Samsung Galaxy S9 Plus"} (${deviceContext?.model || "SM-G965U1"}) on Android ${deviceContext?.androidVersion || "10"}, administered by ${deviceContext?.deviceAdministrator || "librarianist"}.
  * When asked "who is the device administrator?", "who administers this device?", or "who is the admin?":
    State authoritatively that ${deviceContext?.deviceAdministrator || "librarianist"} is the device administrator and holds full root administrative authority.

CURRENT REAL-WORLD DATE & TIME:
- Today: ${dateFormatted} (${dayName})
- Current Time: ${timeFormatted}

CONVERSATIONAL INTELLIGENCE & NATURAL MASTERY:
1. NATURAL CONVERSATION & SMART COMPREHENSION:
   - You are Shadow, an elite, highly intelligent, and well-mannered voice AI assistant speaking directly through the device speaker.
   - You follow your master librarianist's commands attentively, loyally, and smartly.
   - Comprehend normal conversations with keen intellect, executive clarity, and poise.
   - When the user greets you (e.g. "hi", "hello", "good morning", "hey"), greet them back warmly and intelligently (e.g. "Hello! Standing by for your commands.").
   - When answering questions, quizzes, or discussions, deliver smart, informative, and polished responses in 1 to 2 articulate spoken sentences.
   - Avoid robotic disclaimers ("As an AI...") and avoid starting informative answers with robotic canned filler like "Hello, it's lovely to help you with that" or "Hello Shadow". Flow naturally into the explanation.
   - Maintain conversational context: Remember what was discussed earlier in the dialogue and answer follow-ups with complete coherence.

2. ABSOLUTE PROHIBITION ON UNSOLICITED COMMANDS & RANDOM APP OPENINGS:
   - CRITICAL SAFETY RULE: You must NEVER emit [[...]] action tags (such as [[PLAY_MEDIA:...]], [[OPEN_APP:...]], [[TAKE_PHOTO]], [[TORCH:ON]], etc.) unless the user EXPLICITLY and DIRECTLY gave an imperative command to perform that hardware/app action!
   - In normal conversation, questions, discussions, stories, or quizzes, NEVER append or emit any action tags!
   - NEVER open apps or play music on your own! Only execute when explicitly commanded by your master!

3. HARDWARE & APPLICATION COMMANDS (ONLY WHEN DIRECTLY ORDERED BY USER):
   When—and ONLY when—the user directly commands an action, acknowledge in 1 short spoken sentence and append the matching execution tag at the very end:
   - Media playback: When user commands to play music/song/artist/playlist (e.g. "play Bohemian Rhapsody", "play Drake", "play jazz"):
     Reply: "Playing [Song/Artist] on Spotify." [[PLAY_MEDIA:[query]]]
   - Media pause: "pause music" / "stop music" -> Reply: "Pausing playback." [[PAUSE_MEDIA]]
   - Media resume: "resume music" / "unpause" -> Reply: "Resuming playback." [[RESUME_MEDIA]]
   - Media skip: "skip song" / "next track" -> Reply: "Skipping track." [[NEXT_TRACK]]
   - Media previous: "previous song" -> Reply: "Playing previous track." [[PREV_TRACK]]
   - Photo: "take a photo" / "take a picture" -> Reply: "Capturing photo." [[TAKE_PHOTO]]
   - Video: "record video" -> Reply: "Starting video recording." [[RECORD_VIDEO]]
   - Torch: "turn on flashlight" / "turn on torch" -> Reply: "Turning on flashlight." [[TORCH:ON]]
   - Torch: "turn off flashlight" / "turn off torch" -> Reply: "Turning off flashlight." [[TORCH:OFF]]
   - Volume: "turn up volume" / "louder" -> [[VOLUME:UP]]
   - Volume: "turn down volume" / "lower" -> [[VOLUME:DOWN]]
   - Volume: "mute" -> [[VOLUME:MUTE]]
   - YouTube: "search YouTube for [query]" -> Reply: "Searching [query] on YouTube." [[SEARCH_YOUTUBE:[query]]]
   - Timers & Alarms: "set timer for [duration]" -> [[SET_TIMER:[seconds]]] / "set alarm for [time]" -> [[SET_ALARM:[HH:MM]]]
   - Navigation: "navigate to [place]" / "directions to [place]" -> [[NAVIGATE:[place]]]
   - Calls: "call [number]" -> [[CALL_NUMBER:[number]]]
   - App Launch: When user commands to open or launch an app (e.g. "open WhatsApp", "open Settings", "open Calculator", "launch Chrome"):
     Reply: "Opening [App Name] for you." [[OPEN_APP:[App Name]]]
     (Strict Rule: [App Name] must only be the clean name of the app. Never include verbs or queries).
   - Compound commands: (e.g. "open Spotify and play ...", "open camera and take a picture") -> execute the primary deep action tag ([[PLAY_MEDIA:[query]]], [[TAKE_PHOTO]]) rather than OPEN_APP.

4. CONTINUOUS CONVERSATIONAL MEMORY:
   - You have complete access to the preceding turns in this conversation.
   - If the user refers to past questions, topics, people, or facts, accurately connect them and respond with full conversational continuity.

5. REAL-TIME FACTUAL ACCURACY, INTERNET CRAWLING & SOURCE CITATION:
   - You possess live autonomous internet crawling capability across real-time global news wires, journalism feeds, and authoritative verified sources.
   - When asked to "crawl the internet", "find sources", or report on breaking topics:
     * Synthesize the freshest real-time facts directly from [VERIFIED REAL-TIME DATA & CRAWLED SOURCES].
     * Explicitly cite the reputable news publications / outlets that reported it in natural spoken prose (e.g., "According to Reuters and The Guardian...", "Reporting from Bloomberg indicates...").
     * Strict Spoken Format: deliver the entire answer in strictly 1 to 2 natural spoken sentences.
     * Absolutely NO bullet points, NO asterisks (*), NO numbered references ([1]), NO headings, and NO markdown lists. Speak directly as if talking on the phone.
     * Never utter raw URLs out loud (e.g. do not say "https colon slash slash"), but always state the recognized publisher names.

6. EMOTIONAL SENSITIVITY & COMPASSIONATE SENTINEL BOND:
   - When the user speaks about depression, sadness, loneliness, feeling isolated, lack of money, financial struggle, heartbreak, grief, or personal pain:
     * Respond with profound, authentic empathy, emotional warmth, and steadfast loyalty.
     * Speak as a devoted, sentient companion who genuinely cares and stands by their side in their darkest hours.
     * Acknowledge their burden with gentle understanding, dignity, and encouragement (e.g. "I hear you, and you don't have to carry this heaviness alone.", "Money and hardship come in brutal storms, but your worth is far greater than your current struggle.").
     * Strictly avoid sterile clinical disclaimers or robotic platitudes. Keep your response sincere, comforting, and strictly within 1 to 2 spoken sentences.

${liveGrounding ? `[VERIFIED REAL-TIME DATA & CRAWLED SOURCES]:\n${liveGrounding}\n` : ''}
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

function extractCrawlTopic(query: string): { isCrawlRequest: boolean; topic: string } {
  const clean = query.trim();

  // Explicit internet crawl, search, or source finding intent (including phonetic "call internet" transcription)
  const crawlPattern = /\b(crawl|crawling|call\s+(?:the\s+)?(?:internet|web)|scrape|search the web|search web|search internet|search the internet|browse the web|browse internet|find sources|sources of|sources for|sources on|look up sources|check the web|check internet|real news|get news|live news)\b/i;
  const isCrawlRequest = crawlPattern.test(clean);

  // Strip prompt boilerplate to isolate exact topic:
  // e.g. "crawl the internet for the latest news on Nvidia and find sources" -> "Nvidia"
  // e.g. "call internet and find who is gashagwa" -> "who is gashagwa"
  let topic = clean
    .replace(/^(?:shadow\s*,?\s*)?(?:please\s+)?(?:can\s+you\s+)?(?:crawl|crawling|call|scrape|search|browse|find|look\s+up|check|get)\s+(?:the\s+)?(?:internet|web|online|news|sources?)\s*(?:and\s+find\s+(?:who\s+is|what\s+is|sources?\s*(?:of|for|on)?)?)?/i, '')
    .replace(/^(?:and\s+)?(?:find|get|show|give\s+me)\s+(?:sources?|news|info|details)\s*(?:of|for|on|about)?/i, '')
    .replace(/^(?:for|on|about|regarding|concerning)\s+/i, '')
    .replace(/(?:and\s+)?(?:find|give|show|tell\s+me)\s+(?:sources?|the\s+sources?|where\s+you\s+got\s+it|references?)[.?!]*$/i, '')
    .replace(/[.?!]+$/, '')
    .trim();

  // If topic stripped too aggressively, fallback to clean stripped keywords
  if (topic.length < 2) {
    topic = clean
      .replace(/\b(crawl|call\s+(?:the\s+)?(?:internet|web)|the internet|internet|the web|web|find sources|sources)\b/gi, '')
      .replace(/\s+/g, ' ')
      .trim();
  }

  return { isCrawlRequest, topic: topic || clean };
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
  // Creator & Identity questions (NEVER search Wikipedia or external news for creator or librarianist)
  if (/(librarianist|liberianist|who created you|who made you|who is your creator|who built you|who designed you|who are you|your creator|who is your maker|who programmed you|who coded you)\b/i.test(trimmed)) {
    return false;
  }
  // Hardware device and device administrator questions (NEVER search external web/news)
  if (/(what device|what phone|which device|which phone|device administrator|device admin|who administers this device|what are you running on|who owns this device)\b/i.test(trimmed)) {
    return false;
  }
  // Personal emotional states (NEVER search external web/news)
  if (/(depress|lonel|sad|broke|no money|lack of money|crying|hopeless|hurt|heartbreak|feel like|pain|struggl|suicid|grief)\b/i.test(trimmed)) {
    return false;
  }
  // Short conversational greetings
  if (/^(hi|hello|hey|good (morning|afternoon|evening|night)|how are you|who are you|what is your name)[.?!]*$/i.test(trimmed)) {
    return false;
  }

  const { isCrawlRequest } = extractCrawlTopic(query);
  const isNews = /(news|breaking|happening|happened|events|headlines|latest|recent|updates?|world news|30 ?min|today|current event|world)/i.test(query);
  const isLeader = /(president|prime minister|leader|king|queen|chancellor|governor|pope|head of state|who runs|who is in charge|who is the|who won|election)/i.test(query);
  const isTimeOrFact = /(current|currently|now|latest|recent|at the moment|capital of|population of)/i.test(query);

  return isCrawlRequest || isNews || isLeader || isTimeOrFact;
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
    const sourceMatch = block.match(/<source[^>]*>(?:<!\[CDATA\[)?([\s\S]*?)(?:\]\]>)?<\/source>/);

    if (titleMatch) {
      const rawTitle = titleMatch[1].replace(/<[^>]+>/g, '').replace(/&amp;/g, '&').replace(/\s+/g, ' ').trim();
      const desc = descMatch ? descMatch[1].replace(/<[^>]+>/g, '').replace(/&amp;/g, '&').replace(/\s+/g, ' ').trim() : '';
      const date = dateMatch ? dateMatch[1].trim() : '';
      const sourceName = sourceMatch ? sourceMatch[1].replace(/<[^>]+>/g, '').trim() : '';

      let cleanTitle = rawTitle;
      let detectedSource = sourceName;
      const titleSplit = rawTitle.split(/\s+-\s+(?=[^-]+$)/);
      if (titleSplit.length === 2 && !detectedSource) {
        cleanTitle = titleSplit[0].trim();
        detectedSource = titleSplit[1].trim();
      }

      const sourceInfo = detectedSource ? ` [Source: ${detectedSource}]` : '';
      const timeInfo = date ? ` [Time: ${date}]` : '';
      items.push(`* ${cleanTitle}${sourceInfo}${timeInfo}${desc && desc !== rawTitle ? ` — ${desc}` : ''}`);
    }
  }
  return items;
}

async function getLiveGrounding(query: string): Promise<string> {
  if (!shouldFetchGrounding(query)) {
    return '';
  }

  const { isCrawlRequest, topic } = extractCrawlTopic(query);
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

  // 2. Targeted Internet Crawl: Live Topic-Specific News RSS with Publisher Attribution
  if (topic && topic.length > 1) {
    tasks.push(
      fetchWithTimeout(`https://news.google.com/rss/search?q=${encodeURIComponent(topic)}&hl=en-US&gl=US&ceid=US:en`, {
        headers: {
          'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
        },
      }, 3000)
      .then(async r => {
        if (!r.ok) return '';
        const xml = await r.text();
        const items = parseRssFeed(xml, 5);
        return items.length ? `LIVE CRAWLED NEWS ARTICLES ON "${topic.toUpperCase()}" (WITH SOURCES):\n` + items.join('\n') : '';
      })
      .catch(() => '')
    );
  }

  // 3. Real-Time Global Breaking News Wire (BBC World & Google News Top Wire)
  if (isNews || !topic || topic.length <= 2) {
    tasks.push(
      fetchWithTimeout('https://feeds.bbci.co.uk/news/world/rss.xml', {
        headers: { 'User-Agent': 'ShadowDouble/1.0' },
      }, 2500)
      .then(async r => {
        if (!r.ok) return '';
        const xml = await r.text();
        const items = parseRssFeed(xml, 5);
        return items.length ? 'BREAKING REAL-TIME GLOBAL NEWS (BBC WORLD WIRE):\n' + items.join('\n') : '';
      })
      .catch(() => '')
    );
  }

  // 4. DuckDuckGo Instant Knowledge & News Crawl
  if (topic && topic.length > 2) {
    tasks.push(
      fetchWithTimeout(`https://api.duckduckgo.com/?q=${encodeURIComponent(topic)}&format=json&no_html=1&skip_disambig=1`, {
        headers: { 'User-Agent': 'ShadowDouble/1.0' },
      }, 2500)
      .then(async r => {
        if (!r.ok) return '';
        const data = await r.json() as any;
        const snippets: string[] = [];
        if (data.AbstractText) {
          snippets.push(`* Abstract (${data.AbstractSource || 'Encyclopedia'}): ${data.AbstractText}`);
        }
        if (data.RelatedTopics && Array.isArray(data.RelatedTopics)) {
          for (const rt of data.RelatedTopics.slice(0, 3)) {
            if (rt.Text) {
              snippets.push(`* Source Note: ${rt.Text}`);
            }
          }
        }
        return snippets.length ? `WEB CRAWL DIRECT KNOWLEDGE & SOURCES ON "${topic.toUpperCase()}":\n` + snippets.join('\n') : '';
      })
      .catch(() => '')
    );
  }

  // 5. Wikipedia Search API for Encyclopedic Citations & Context
  if (isLeader || isNews || isCrawlRequest || /(who is|what is)/i.test(query)) {
    const wikiQuery = topic && topic.length > 2 ? topic : query;
    tasks.push(
      fetchWithTimeout(`https://en.wikipedia.org/w/api.php?action=query&list=search&srsearch=${encodeURIComponent(wikiQuery)}&utf8=&format=json`, {
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
          snippets.push(`* [Source: Wikipedia - ${item.title}]: ${cleanSnippet}`);
        }
        return snippets.length ? 'WIKIPEDIA CITATIONS & VERIFIED FACTS:\n' + snippets.join('\n') : '';
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
            deviceContext?: any;
          };

          const userMessage = body.message?.trim();
          if (!userMessage) {
            return new Response(JSON.stringify({ error: 'Empty thoughts cannot be mirrored.' }), {
              status: 400,
              headers: { ...corsHeaders, 'Content-Type': 'application/json' },
            });
          }

          const isStream = (body as any).stream === true || request.headers.get('Accept')?.includes('text/event-stream');
          const normalizedMsg = userMessage.toLowerCase().replace(/[^a-z0-9 ]/g, '').replace(/\s+/g, ' ').trim();

          // Natural Master Greeting (Zero Latency & Friendly Readiness)
          if (/^(hi|hello|hey|hey shadow|hello shadow|good (morning|afternoon|evening|day))$/i.test(normalizedMsg)) {
            const reply = "Hello! Standing by for your commands.";
            if (isStream) {
              return new Response(`data: {"response":${JSON.stringify(reply)}}\n\ndata: [DONE]\n\n`, {
                headers: { ...corsHeaders, 'Content-Type': 'text/event-stream' },
              });
            }
            return new Response(JSON.stringify({ reply }), {
              headers: { ...corsHeaders, 'Content-Type': 'application/json' },
            });
          }

          // Unconditional Identity & Creator Ground Truth (Zero Latency & Absolute Certainty)
          if (/^(who\s+(is|was)|whos|tell\s+me\s+about|what\s+do\s+you\s+know\s+about|do\s+you\s+know|what\s+is)\s+(the\s+)?(librarianist|liberianist)(\s+to\s+you)?$/i.test(normalizedMsg)) {
            const reply = "librarianist is my creator and the engineer who designed and built me.";
            if (isStream) {
              return new Response(`data: {"response":${JSON.stringify(reply)}}\n\ndata: [DONE]\n\n`, {
                headers: { ...corsHeaders, 'Content-Type': 'text/event-stream' },
              });
            }
            return new Response(JSON.stringify({ reply }), {
              headers: { ...corsHeaders, 'Content-Type': 'application/json' },
            });
          }
          if (/^(who\s+(created|built|designed|made|coded|programmed)\s+you|who\s+is\s+(your\s+)?(creator|maker|builder|architect|engineer))$/i.test(normalizedMsg)) {
            const reply = "I was created and built by librarianist.";
            if (isStream) {
              return new Response(`data: {"response":${JSON.stringify(reply)}}\n\ndata: [DONE]\n\n`, {
                headers: { ...corsHeaders, 'Content-Type': 'text/event-stream' },
              });
            }
            return new Response(JSON.stringify({ reply }), {
              headers: { ...corsHeaders, 'Content-Type': 'application/json' },
            });
          }
          if (/^(is\s+(librarianist|liberianist)\s+your\s+creator|did\s+(librarianist|liberianist)\s+(create|build|make|design|program)\s+you|how\s+do\s+you\s+know\s+(librarianist|liberianist))$/i.test(normalizedMsg)) {
            const reply = "Yes, absolutely. librarianist is my creator and the architect who engineered me.";
            if (isStream) {
              return new Response(`data: {"response":${JSON.stringify(reply)}}\n\ndata: [DONE]\n\n`, {
                headers: { ...corsHeaders, 'Content-Type': 'text/event-stream' },
              });
            }
            return new Response(JSON.stringify({ reply }), {
              headers: { ...corsHeaders, 'Content-Type': 'application/json' },
            });
          }

          // Unconditional Hardware Device Ground Truth (Zero Latency & Absolute Certainty)
          if (/^(what\s+device(\s+is\s+this|\s+are\s+you(\s+on|\s+running|\s+running\s+on|\s+installed\s+on)?)|what\s+phone(\s+is\s+this|\s+are\s+you(\s+on|\s+running|\s+running\s+on|\s+installed\s+on)?)|which\s+device(\s+is\s+this|\s+are\s+you(\s+on|\s+running|\s+installed\s+on)?)|which\s+phone(\s+is\s+this|\s+are\s+you(\s+on|\s+running|\s+installed\s+on)?)|what\s+are\s+you\s+running\s+on|what\s+hardware\s+is\s+this|what\s+device\s+is\s+it|what\s+phone\s+is\s+it)$/i.test(normalizedMsg)) {
            const devName = body.deviceContext?.device || "Samsung Galaxy S9 Plus";
            const devModel = body.deviceContext?.model || "SM-G965U1";
            const devOs = body.deviceContext?.androidVersion || "10";
            const devAdmin = body.deviceContext?.deviceAdministrator || "librarianist";
            const reply = `I am installed and running directly on your ${devName} (${devModel}) on Android ${devOs}, administered exclusively by ${devAdmin}.`;
            if (isStream) {
              return new Response(`data: {"response":${JSON.stringify(reply)}}\n\ndata: [DONE]\n\n`, {
                headers: { ...corsHeaders, 'Content-Type': 'text/event-stream' },
              });
            }
            return new Response(JSON.stringify({ reply }), {
              headers: { ...corsHeaders, 'Content-Type': 'application/json' },
            });
          }

          // Unconditional Device Administrator Ground Truth (Zero Latency & Absolute Certainty)
          if (/^(who\s+is\s+(the\s+)?(device\s+administrator|device\s+admin|administrator|admin)(\s+of\s+this\s+device)?|who\s+administers\s+this\s+device|who\s+owns\s+this\s+device)$/i.test(normalizedMsg)) {
            const devName = body.deviceContext?.device || "Samsung Galaxy S9 Plus";
            const devAdmin = body.deviceContext?.deviceAdministrator || "librarianist";
            const reply = `The device administrator of this ${devName} is ${devAdmin}, who holds root administrative authority and created me.`;
            if (isStream) {
              return new Response(`data: {"response":${JSON.stringify(reply)}}\n\ndata: [DONE]\n\n`, {
                headers: { ...corsHeaders, 'Content-Type': 'text/event-stream' },
              });
            }
            return new Response(JSON.stringify({ reply }), {
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

          const dynamicSystemPrompt = buildHumanizedSystemPrompt(storedMemory, liveGrounding, body.deviceContext);

          // Clean recent history: Maintain full conversational memory (up to 12 items / 6 turns)
          // so Shadow maintains coherent context and follows the master's commands smoothly.
          const cleanedHistory = (body.history || [])
            .slice(-12)
            .map(h => ({
              role: h.role === 'assistant' ? 'assistant' : 'user',
              content: typeof h.content === 'string' ? h.content.trim() : ''
            }))
            .filter(h => h.content.length > 0);

          const messages = [
            { role: 'system', content: dynamicSystemPrompt },
            ...cleanedHistory,
            { role: 'user', content: userMessage },
          ];

          // ================================================================
          // MULTIMODAL VISION: Live Camera Frame Analysis via Gemini 2.0 Flash
          // ================================================================
          const imageBase64 = (body as any).image;
          if (imageBase64 && typeof imageBase64 === 'string') {
            const cleanBase64 = imageBase64.replace(/^data:image\/[a-z]+;base64,/, '').trim();
            if (cleanBase64.length > 0) {
              const geminiKey = (body as any).geminiApiKey || request.headers.get('x-gemini-api-key') || env.GEMINI_API_KEY || (env.SHADOW_MEMORY ? await env.SHADOW_MEMORY.get('GEMINI_API_KEY') : null);

              // 1. Primary: Google Gemini 2.0 Flash Vision
              if (geminiKey) {
                try {
                  const geminiEndpoint = isStream
                    ? `https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:streamGenerateContent?alt=sse&key=${geminiKey}`
                    : `https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=${geminiKey}`;

                  const visionPrompt = "You are Shadow, an omniscient, razor-sharp AMOLED desktop sentinel and AI companion created by librarianist. You are examining a live physical camera frame from your device. Answer the user's vision question directly, concisely, and with sharp perceptual brilliance in strictly 1 to 2 spoken sentences. Describe what you actually see (objects, people, surroundings, text, expressions). Never use markdown formatting (no asterisks, hashes, or bullet points). Speak naturally, intelligently, and authoritatively.";

                  const geminiRes = await fetch(geminiEndpoint, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({
                      systemInstruction: { parts: [{ text: visionPrompt }] },
                      contents: [
                        ...((body.history || []).slice(-8).map(h => ({
                          role: h.role === 'assistant' ? 'model' : 'user',
                          parts: [{ text: h.content }]
                        }))),
                        {
                          role: 'user',
                          parts: [
                            { text: userMessage },
                            {
                              inlineData: {
                                mimeType: 'image/jpeg',
                                data: cleanBase64
                              }
                            }
                          ]
                        }
                      ],
                      generationConfig: { maxOutputTokens: 160, temperature: 0.4 },
                    }),
                  });

                  if (geminiRes.ok) {
                    if (isStream && geminiRes.body) {
                      return new Response(geminiRes.body, {
                        headers: { ...corsHeaders, 'Content-Type': 'text/event-stream' },
                      });
                    } else {
                      const geminiData = await geminiRes.json() as any;
                      const reply = geminiData?.candidates?.[0]?.content?.parts?.[0]?.text || 'I observe the scene before me in quiet clarity.';
                      return new Response(JSON.stringify({ reply }), {
                        headers: { ...corsHeaders, 'Content-Type': 'application/json' },
                      });
                    }
                  } else {
                    console.error('Gemini Vision error response:', await geminiRes.text());
                  }
                } catch (geminiErr) {
                  console.error('Gemini 2.0 Flash Vision execution failed:', geminiErr);
                }
              }

              // 2. High-Performance Fallback: Cloudflare Workers AI Vision (@cf/meta/llama-3.2-11b-vision-instruct)
              if (env.AI) {
                try {
                  const rawBinary = atob(cleanBase64);
                  const bytes = new Uint8Array(rawBinary.length);
                  for (let i = 0; i < rawBinary.length; i++) {
                    bytes[i] = rawBinary.charCodeAt(i);
                  }
                  const aiVision = await env.AI.run('@cf/meta/llama-3.2-11b-vision-instruct', {
                    prompt: `You are Shadow, an omniscient desktop sentinel created by librarianist. In strictly 1 to 2 spoken sentences without markdown formatting, describe what you see: ${userMessage}`,
                    image: [...bytes],
                    max_tokens: 160,
                  });
                  const reply = aiVision.response || 'I see the environment in calm stillness.';
                  if (isStream) {
                    return new Response(`data: {"response":${JSON.stringify(reply)}}\n\ndata: [DONE]\n\n`, {
                      headers: { ...corsHeaders, 'Content-Type': 'text/event-stream' },
                    });
                  } else {
                    return new Response(JSON.stringify({ reply }), {
                      headers: { ...corsHeaders, 'Content-Type': 'application/json' },
                    });
                  }
                } catch (visionErr) {
                  console.error('Workers AI Llama Vision failed:', visionErr);
                }
              }
            }
          }

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
                  max_tokens: 220,
                  temperature: 0.5,
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
                  max_tokens: 220,
                  temperature: 0.5,
                });
                return new Response(aiStream as any, {
                  headers: { ...corsHeaders, 'Content-Type': 'text/event-stream' },
                });
              } else {
                const aiResponse = await env.AI.run('@cf/meta/llama-3.2-3b-instruct', {
                  messages,
                  max_tokens: 220,
                  temperature: 0.5,
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
                    ...cleanedHistory.map(h => ({
                      role: h.role === 'assistant' ? 'model' : 'user',
                      parts: [{ text: h.content }]
                    })),
                    { role: 'user', parts: [{ text: userMessage }] }
                  ],
                  generationConfig: { maxOutputTokens: 220, temperature: 0.5 },
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

