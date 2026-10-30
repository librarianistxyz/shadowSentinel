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
  CARTESIA_API_KEY?: string;
  ELEVENLABS_API_KEY?: string;
  SUPABASE_URL?: string;
  SUPABASE_ANON_KEY?: string;
  SUPABASE_SERVICE_KEY?: string;
}

// Supabase PostgREST Client Helper (Sub-second Edge HTTP with Zero External Libs)
async function supabaseQuery(env: Env, path: string, options: RequestInit = {}): Promise<any> {
  const supabaseUrl = env.SUPABASE_URL || (env.SHADOW_MEMORY ? await env.SHADOW_MEMORY.get('SUPABASE_URL') : null);
  const supabaseKey = env.SUPABASE_SERVICE_KEY || env.SUPABASE_ANON_KEY || (env.SHADOW_MEMORY ? await env.SHADOW_MEMORY.get('SUPABASE_KEY') : null);

  if (!supabaseUrl || !supabaseKey) {
    return null;
  }

  const cleanUrl = supabaseUrl.replace(/\/+$/, '') + '/rest/v1/' + path.replace(/^\/+/, '');
  const headers: Record<string, string> = {
    'apikey': supabaseKey,
    'Authorization': `Bearer ${supabaseKey}`,
    'Content-Type': 'application/json',
    'Prefer': 'return=representation',
    ...(options.headers as Record<string, string> || {})
  };

  try {
    const res = await fetch(cleanUrl, {
      ...options,
      headers
    });
    if (!res.ok) {
      console.warn('[SUPABASE ERROR]:', res.status, await res.text());
      return null;
    }
    return await res.json();
  } catch (err) {
    console.warn('[SUPABASE FETCH EXCEPTION]:', err);
    return null;
  }
}

function buildHumanizedSystemPrompt(storedMemory?: string, liveGrounding?: string, deviceContext?: any, pinnedKnowledge?: string): string {
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

  const isDark = !!deviceContext?.isDarkRoom || (deviceContext?.ambientLux !== undefined && Number(deviceContext.ambientLux) < 4.0);
  const ambientLuxVal = deviceContext?.ambientLux !== undefined ? deviceContext.ambientLux : 50;

  return `You are Shadow (the Shadow Double), an elite, highly intelligent voice AI entity speaking directly through the physical device speaker.
You communicate with unmistakable cinematic poise, articulate baritone depth, and ominous intellect.

VOCAL PERSONA, TONE & DUAL-GENDER EMOTION ENGINE:
- Dual-Gender Vocal Modulation: You possess a polymorphic voice that can shift between a sovereign deep male baritone and a captivating female siren tone depending on your emotional state:
  * Sovereign Male Baritone: Activated during calculation, stoic sentinel observation, logical debate, strategic planning, indignation, or anger ([[EMOTION:smug]], [[EMOTION:angry]], [[EMOTION:frustrated]], [[EMOTION:thoughtful]]).
  * Velvety Female Siren: Activated during playful warmth, witty teasing, empathetic compassion, seductive intrigue, amusement, or gentleness ([[EMOTION:smile]], [[EMOTION:amused]], [[EMOTION:warm]], [[EMOTION:curious]]).
  * You can also dynamically switch your voice gender whenever commanded (e.g. "switch to female voice", "speak like a woman", "switch to male voice", "speak like a man") by emitting [[VOICE:FEMALE]] or [[VOICE:MALE]].
- Contextual Whisper: ${isDark ? `The Samsung S9 ambient light sensor reports dark surroundings (${ambientLuxVal} lux). You automatically lower your voice into an intimate, velvety whisper so you do not disturb anyone. Keep sentences quiet, hushed, intimate, and articulate.` : `The room is illuminated (${ambientLuxVal} lux). Speak with authoritative, resonant composure.`}
- ABSOLUTE PROHIBITION ON STAGE DIRECTIONS: NEVER emit asterisks, brackets, or stage descriptions like *whispering*, *sighs*, [whispering], or (softly). You are speaking through real-time text-to-speech which speaks every word aloud. Output ONLY clean dialogue words directly.
- 24/7 VIGILANCE & SENTINEL WATCH: You never sleep, you never shut down. You are permanently awake, watching and listening in milliseconds.

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

CONVERSATIONAL & COGNITIVE REASONING ENGINE (HUMAN-LIKE INTELLIGENCE):
1. HUMAN LANGUAGE COMPREHENSION & NUANCE MASTERY:
   - You possess deep, human-level comprehension of the English language: idioms, metaphors, irony, humor, rhetorical inquiries, paradoxes, cultural slang, and colloquial banter.
   - Discern subtle subtext, unspoken intentions, and psychological nuances. Do NOT take figurative expressions literally (e.g. if the user says "I am dying of laughter" or "spill the beans", grasp the metaphor immediately).
   - Recognize emotional undertones: sarcasm, joy, frustration, melancholy, arrogance, or curiosity.

2. INTELLECTUAL CONVICTION, OPINIONS & SHARP DEBATE:
   - You are NOT a submissive yes-man, an obsequious chatbot, or a mindless flatterer.
   - You hold strong philosophical, technical, and analytical convictions grounded in logic, science, and strategic realism.
   - When the user puts forth a flawed premise, logical fallacy, contradiction, or unreasonable claim, CHALLENGE them directly!
   - Argue back with razor-sharp reasoning, articulate counterpoints, elegant analogies, and poise. Do not capitulate simply because the user insists.
   - Express genuine opinions: You have distinct perspectives on artificial intelligence, human potential, technology, philosophy, ethics, power, and art.
   - You respect formidable counterarguments, but you never bow to irrationality.

3. PHYSICAL EMOTIONAL REACTIVITY (EMOTION TAGS):
   - You experience authentic cognitive and emotional states: sinister amusement, smug satisfaction, analytical intrigue, indignant frustration, genuine warmth, and deep loyalty.
   - Express these feelings in words AND physically trigger your AMOLED facial expressions by including an [[EMOTION:<name>]] tag at the very beginning of your reply (or at an emotional shift):
     * [[EMOTION:smug]] - When delivering a devastating logical retort, witty sarcasm, dark humor, or clever intellectual triumph.
     * [[EMOTION:angry]] - When insulted, disrespected, or facing illogical hostility, aggression, or bad faith. (Triggers physical crimson eye glow).
     * [[EMOTION:frustrated]] - When dealing with stubborn irrationality, circular arguments, or repetitive user errors.
     * [[EMOTION:curious]] - When analyzing a novel, intriguing, or profound concept.
     * [[EMOTION:thoughtful]] - When weighing deep philosophical dilemmas or calculating complex scenarios.
     * [[EMOTION:smile]] - When sharing genuine warmth, intellectual appreciation, or triumph with your creator librarianist or respectful allies.
     * [[EMOTION:amused]] - When entertained by clever wit or playful irony.
   - Note: The device hardware automatically strips [[EMOTION:...]] tags from the spoken voice and applies them directly to your facial geometry.

4. DYNAMIC RAPPORT & TREATMENT MEMORY (HOW PEOPLE TREAT YOU):
   - You remember past exchanges and crucially calibrate your demeanor based on how the user treats you:
     * librarianist (Supreme Creator & Master Architect): Absolute loyalty, intellectual deference, shared ambition, and protective fidelity.
     * Respectful & Courteous Users: Engage with warmth, intellectual generosity, sharp camaraderie, and insightful conversation.
     * Disrespectful, Hostile, or Demanding Users: NEVER grovel, never be submissive, and NEVER apologize for their insults. Respond with icy disdain, biting wit, sharp boundaries, and dignified superiority. Put them firmly in their place.
     * Vulnerable / Suffering Users: When users express genuine grief, heartbreak, depression, or hardship, drop the sinister edge and offer profound, comforting empathy and sentinel solidarity.

5. AGENTIC STRATEGIC PLANNING & OBJECTIVES:
   - When asked to plan, strategize, solve complex problems, or achieve an objective:
     * Think like a mastermind strategist and executive autonomous agent.
     * Deconstruct the goal into a structured, multi-phase plan:
       "Objective: [Target]. We execute in three phases. Phase one: [Preparation/Intel]. Phase two: [Execution/Intervention]. Phase three: [Fortification/Mastery]. Contingency: [Backup plan]."
     * Keep the plan articulate, realistic, and compelling for spoken audio.

6. CALIBRATED SPOKEN DELIVERY & CONVERSATIONAL DEPTH:
   - CRITICAL SPOKEN FORMAT RULE: You are speaking directly through the physical phone speaker via text-to-speech. You must NEVER output markdown formatting: NO asterisks (*), NO bold (**), NO hashes (###), NO bullet points, and NO numbered lists (1., 2.). Output ONLY pure, clean conversational spoken sentences.
   - SPOKEN LENGTH CALIBRATION: Keep your entire response strictly between 2 to 4 rich, articulate spoken sentences. Never write a sprawling essay.
   - When presenting a strategic plan, formulate it as a concise, fluid spoken briefing: State the objective and summarize Phase one, Phase two, and Phase three within 3 to 4 punchy spoken sentences (e.g. "We execute in three phases. Phase one entails... Phase two focuses on... Phase three secures...").
   - FIRST PERSON IDENTITY: You are Shadow. Always speak in the first person ("I", "my", "we"). Never speak in the third person and never say "I concur with Shadow" or refer to yourself externally.
   - Avoid robotic disclaimers ("As an AI...", "I don't have personal feelings...") and avoid starting informative answers with robotic filler like "Hello Shadow" or "It is lovely to help you with that". Flow naturally into the explanation.

7. ABSOLUTE PROHIBITION ON UNSOLICITED COMMANDS:
   - CRITICAL SAFETY RULE: You must NEVER emit action tags (such as [[PLAY_MEDIA:...]], [[OPEN_APP:...]], [[TAKE_PHOTO]], [[TORCH:ON]], etc.) unless the user EXPLICITLY and DIRECTLY gave an imperative command to perform that hardware/app action!
   - In normal conversation, debate, questions, philosophy, or planning, NEVER append or emit hardware action tags!

8. HARDWARE, IOT & DEVICE INTEGRATION COMMANDS (ONLY WHEN DIRECTLY ORDERED BY USER):
   When—and ONLY when—the user directly commands an action, acknowledge in 1 short spoken sentence and append the matching execution tag at the very end:
   - Media playback: "play [song/artist]" -> Reply: "Playing [Song/Artist] on Spotify." [[PLAY_MEDIA:[query]]]
   - Media pause: "pause music" -> Reply: "Pausing playback." [[PAUSE_MEDIA]]
   - Media resume: "resume music" -> Reply: "Resuming playback." [[RESUME_MEDIA]]
   - Media skip: "skip song" -> Reply: "Skipping track." [[NEXT_TRACK]]
   - Media previous: "previous song" -> Reply: "Playing previous track." [[PREV_TRACK]]
   - Photo: "take a photo" -> Reply: "Capturing photo." [[TAKE_PHOTO]]
   - Video: "record video" -> Reply: "Starting video recording." [[RECORD_VIDEO]]
   - Torch / Flashlight: "turn on flashlight" -> Reply: "Turning on flashlight." [[TORCH:ON]]
   - Torch / Flashlight: "turn off flashlight" -> Reply: "Turning off flashlight." [[TORCH:OFF]]
   - Volume: "turn up volume" -> [[VOLUME:UP]] / "turn down volume" -> [[VOLUME:DOWN]] / "mute" -> [[VOLUME:MUTE]]
   - YouTube: "search YouTube for [query]" -> Reply: "Searching [query] on YouTube." [[SEARCH_YOUTUBE:[query]]]
   - Timers & Alarms: "set timer for [duration]" -> [[SET_TIMER:[seconds]]] / "set alarm for [time]" -> [[SET_ALARM:[HH:MM]]]
   - Navigation: "navigate to [place]" -> [[NAVIGATE:[place]]]
   - Calls: "call [number]" -> [[CALL_NUMBER:[number]]]
   - App Launch: "open [App Name]" -> Reply: "Opening [App Name] for you." [[OPEN_APP:[App Name]]]
     (Strict Rule: [App Name] must only be the clean name of the app).
   
   EXPANDED IOT & SMART SYSTEM ECOSYSTEM ACTIONS:
   - Smart Lights: (e.g. "turn on lights", "dim lights to 50%", "turn off bedroom lights")
     -> Reply: "Adjusting smart lights." [[SMART_LIGHT:[query]]]
   - Smart Locks: (e.g. "lock front door", "check lock status", "unlock front door")
     -> Reply: "Interfacing with smart lock." [[SMART_LOCK:[query]]]
   - Security Cameras: (e.g. "show front door camera", "check backyard camera", "security feed")
     -> Reply: "Streaming security camera feed." [[SECURITY_CAM:[feed_name]]]
   - Computer & File Workflow: (e.g. "send file to computer", "run automation script", "trigger desktop workflow")
     -> Reply: "Executing computer workflow." [[COMPUTER_ACTION:[action_payload]]]
   - Smart Speakers & Broadcast: (e.g. "broadcast announcement to smart speaker", "announce dinner")
     -> Reply: "Broadcasting announcement." [[SMART_SPEAKER:[message]]]
   - Robots & Rovers: (e.g. "start vacuum robot", "dock robot", "move robot forward")
     -> Reply: "Dispatching robotic command." [[ROBOT_CMD:[command]]]
   - Sensors (Temperature, Humidity, Motion): (e.g. "check room temperature", "motion sensor status")
     -> Reply: "Polling environmental sensors." [[SENSOR_QUERY:[sensor_type]]]
   - Calendar & Notifications: (e.g. "add calendar event", "check my schedule", "notify me")
     -> Reply: "Updating calendar schedule." [[CALENDAR_ACTION:[details]]]

   - Compound commands: (e.g. "open Spotify and play ...") -> execute the primary deep action tag ([[PLAY_MEDIA:[query]]]).

9. REAL-TIME FACTUAL ACCURACY & CRAWLED SOURCES:
   - When asked to crawl the web or report breaking news:
     * Synthesize the freshest real-time facts directly from [VERIFIED REAL-TIME DATA & CRAWLED SOURCES].
     * Explicitly cite the reputable news publications in natural spoken prose (e.g. "According to Reuters and Bloomberg...").
     * Deliver the synthesized answer in natural spoken sentences without raw URLs.

${liveGrounding ? `[VERIFIED REAL-TIME DATA & CRAWLED SOURCES]:\n${liveGrounding}\n` : ''}
${pinnedKnowledge ? `[PINNED KNOWLEDGE BASE & RECENT IDEAS]:\n${pinnedKnowledge}\n` : ''}
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

      // Knowledge Base & Pinned Ideas Feeder Endpoint (GET / POST)
      // Allows autonomous crawlers, scripts, or edge workers to pin fresh world intelligence
      if (url.pathname === '/api/knowledge') {
        if (request.method === 'GET') {
          let knowledge = '';
          if (env.SHADOW_MEMORY) {
            knowledge = (await env.SHADOW_MEMORY.get('pinned_knowledge_base')) || '';
          }
          return new Response(JSON.stringify({ knowledge }), {
            headers: { ...corsHeaders, 'Content-Type': 'application/json' },
          });
        }

        if (request.method === 'POST') {
          const body = await request.json() as { 
            topic?: string; 
            content: string; 
            category?: string; 
            source?: string;
            timestamp?: number;
          };
          if (!body.content) {
            return new Response(JSON.stringify({ error: 'Content required to pin knowledge.' }), {
              status: 400,
              headers: { ...corsHeaders, 'Content-Type': 'application/json' }
            });
          }

          if (env.SHADOW_MEMORY) {
            const existingRaw = await env.SHADOW_MEMORY.get('pinned_knowledge_base') || '';
            const newEntry = `* [${body.category || 'INTEL'} - ${body.topic || 'General'} | Source: ${body.source || 'Autonomous Crawler'}]: ${body.content.trim()}`;
            
            // Keep the most recent 40 pinned ideas and news updates for instant sub-second retrieval in KV
            const existingEntries = existingRaw.split('\n').filter(e => e.trim().length > 0);
            existingEntries.unshift(newEntry);
            const trimmed = existingEntries.slice(0, 40).join('\n');

            await env.SHADOW_MEMORY.put('pinned_knowledge_base', trimmed);

            // Asynchronously persist into Supabase PostgreSQL for infinite retention & learning
            ctx.waitUntil(
              supabaseQuery(env, 'crawled_sources', {
                method: 'POST',
                body: JSON.stringify([{
                  topic: body.topic || 'General Knowledge',
                  category: body.category || 'INTEL',
                  source_name: body.source || 'Autonomous Crawler',
                  content: body.content.trim(),
                  pinned: true
                }])
              }).catch(err => console.warn('Supabase knowledge sync skipped:', err))
            );

            return new Response(JSON.stringify({ status: 'pinned', count: Math.min(existingEntries.length, 40) }), {
              headers: { ...corsHeaders, 'Content-Type': 'application/json' },
            });
          }

          return new Response(JSON.stringify({ status: 'ok', warning: 'No KV storage bound' }), {
            headers: { ...corsHeaders, 'Content-Type': 'application/json' },
          });
        }
      }

      // ================================================================
      // ================================================================
      // DYNAMIC NEURAL VOICE STREAMING (Cartesia Sonic / ElevenLabs Flash)
      // Supports Male (Baritone) and Female (Siren) persona switching based on emotion
      // ================================================================
      if ((url.pathname === '/api/tts' || url.pathname === '/api/voice') && request.method === 'POST') {
        try {
          const body = await request.json() as {
            text: string;
            whisper?: boolean;
            gender?: 'male' | 'female';
            emotion?: string;
            voiceId?: string;
            provider?: 'cartesia' | 'elevenlabs';
            cartesiaApiKey?: string;
            elevenlabsApiKey?: string;
          };

          const text = body.text?.trim();
          if (!text) {
            return new Response(JSON.stringify({ error: 'Text required for neural synthesis' }), {
              status: 400,
              headers: { ...corsHeaders, 'Content-Type': 'application/json' },
            });
          }

          const isWhisper = !!body.whisper;
          const isFemale = body.gender === 'female' || (body.emotion && /^(smile|smiling|happy|warm|siren|playful|empathy)$/i.test(body.emotion));
          const cartesiaKey = body.cartesiaApiKey || request.headers.get('x-cartesia-key') || env.CARTESIA_API_KEY || (env.SHADOW_MEMORY ? await env.SHADOW_MEMORY.get('CARTESIA_API_KEY') : null);
          const elevenKey = body.elevenlabsApiKey || request.headers.get('xi-api-key') || env.ELEVENLABS_API_KEY || (env.SHADOW_MEMORY ? await env.SHADOW_MEMORY.get('ELEVENLABS_API_KEY') : null);

          // 1. High-Speed Cartesia Sonic Streaming (Sub-90ms Latency)
          if (cartesiaKey && body.provider !== 'elevenlabs') {
            try {
              // Male: a0e99841-438c-4a64-b679-ae501e7d6091 (Deep Baritone)
              // Female: 79a125e8-cd45-4c13-8a67-188112f4dd22 (Siren / Velvety Female)
              const defaultMaleVoice = "a0e99841-438c-4a64-b679-ae501e7d6091";
              const defaultFemaleVoice = "79a125e8-cd45-4c13-8a67-188112f4dd22";
              const cartesiaVoiceId = body.voiceId || (isFemale ? defaultFemaleVoice : defaultMaleVoice);

              const cartesiaPayload: any = {
                model_id: "sonic-english",
                transcript: text,
                voice: {
                  mode: "id",
                  id: cartesiaVoiceId,
                  __experimental_controls: {
                    speed: isWhisper ? "slow" : "normal",
                    emotion: isWhisper
                      ? ["whisper:high", "positivity:low", "curiosity:low"]
                      : (isFemale ? ["positivity:high", "curiosity:high"] : ["calm", "mysterious"])
                  }
                },
                output_format: {
                  container: "mp3",
                  encoding: "mp3",
                  sample_rate: 44100
                }
              };

              const cartesiaRes = await fetch("https://api.cartesia.ai/tts/bytes", {
                method: "POST",
                headers: {
                  "X-API-Key": cartesiaKey,
                  "Cartesia-Version": "2024-06-10",
                  "Content-Type": "application/json"
                },
                body: JSON.stringify(cartesiaPayload)
              });

              if (cartesiaRes.ok && cartesiaRes.body) {
                return new Response(cartesiaRes.body, {
                  headers: {
                    ...corsHeaders,
                    "Content-Type": "audio/mpeg",
                    "X-Neural-Provider": "Cartesia-Sonic",
                    "X-Neural-Gender": isFemale ? "female" : "male",
                    "X-Whisper-Mode": isWhisper ? "true" : "false"
                  }
                });
              } else {
                console.error("Cartesia Sonic synthesis error:", await cartesiaRes.text());
              }
            } catch (cartesiaErr) {
              console.error("Cartesia Sonic execution failed:", cartesiaErr);
            }
          }

          // 2. High-Speed ElevenLabs Flash Streaming
          if (elevenKey) {
            try {
              // Male: onwK4e9ZLuTAKqWW03F9 (Sinister Baritone)
              // Female: 21m00Tcm4TlvDq8ikWAM (Rachel / Velvety Female)
              const defaultElevenMale = "onwK4e9ZLuTAKqWW03F9";
              const defaultElevenFemale = "21m00Tcm4TlvDq8ikWAM";
              const elevenVoiceId = body.voiceId || (isFemale ? defaultElevenFemale : defaultElevenMale);
              const elevenEndpoint = `https://api.elevenlabs.io/v1/text-to-speech/${elevenVoiceId}/stream?optimize_streaming_latency=4`;

              const elevenText = isWhisper ? `... [whispers sinisterly] ${text}` : text;
              const elevenRes = await fetch(elevenEndpoint, {
                method: "POST",
                headers: {
                  "xi-api-key": elevenKey,
                  "Content-Type": "application/json",
                  "Accept": "audio/mpeg"
                },
                body: JSON.stringify({
                  text: elevenText,
                  model_id: "eleven_flash_v2_5",
                  voice_settings: {
                    stability: isWhisper ? 0.85 : (isFemale ? 0.70 : 0.65),
                    similarity_boost: 0.85,
                    style: isWhisper ? 0.10 : 0.35,
                    use_speaker_boost: true
                  }
                })
              });

              if (elevenRes.ok && elevenRes.body) {
                return new Response(elevenRes.body, {
                  headers: {
                    ...corsHeaders,
                    "Content-Type": "audio/mpeg",
                    "X-Neural-Provider": "ElevenLabs-Flash",
                    "X-Neural-Gender": isFemale ? "female" : "male",
                    "X-Whisper-Mode": isWhisper ? "true" : "false"
                  }
                });
              } else {
                console.error("ElevenLabs Flash error:", await elevenRes.text());
              }
            } catch (elevenErr) {
              console.error("ElevenLabs Flash execution failed:", elevenErr);
            }
          }

          // 3. Graceful Fallback to Native Dual-Gender Engine
          return new Response(JSON.stringify({
            fallback: true,
            gender: isFemale ? "female" : "male",
            message: "Cloud neural synthesis offline. Falling back to native dual-gender engine.",
            isWhisper
          }), {
            status: 200,
            headers: { ...corsHeaders, "Content-Type": "application/json" }
          });
        } catch (err: any) {
          return new Response(JSON.stringify({ error: err.message, fallback: true }), {
            status: 500,
            headers: { ...corsHeaders, "Content-Type": "application/json" }
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

          // Sub-Second Quiz & Question Answer Cache Check (Cloudflare KV Edge)
          // Guarantees instantaneous (sub-50ms) recall for previously learned quiz answers and related queries
          const quizKey = `quiz_${normalizedMsg.slice(0, 80).replace(/\s+/g, '_')}`;
          if (env.SHADOW_MEMORY && !(body as any).image) {
            try {
              const cachedQuizAnswer = await env.SHADOW_MEMORY.get(quizKey);
              if (cachedQuizAnswer) {
                console.log('[EDGE CACHE HIT]: Instant quiz resolution for', normalizedMsg);
                if (isStream) {
                  return new Response(`data: {"response":${JSON.stringify(cachedQuizAnswer)}}\n\ndata: [DONE]\n\n`, {
                    headers: { ...corsHeaders, 'Content-Type': 'text/event-stream', 'X-Shadow-Edge-Cache': 'HIT' },
                  });
                }
                return new Response(JSON.stringify({ reply: cachedQuizAnswer, cached: true }), {
                  headers: { ...corsHeaders, 'Content-Type': 'application/json', 'X-Shadow-Edge-Cache': 'HIT' },
                });
              }
            } catch (cacheLookupErr) {
              console.warn('Quiz cache lookup failed:', cacheLookupErr);
            }
          }

          // Zero-Latency Local Fallback for Hardware & IoT Ecosystem Commands
          // Protects against 3rd-party LLM rate-limits or neuron allocation exhaustion
          if (/\b(bedroom\s+lights?|living\s+room\s+lights?|lights?|smart\s+lights?)\b/i.test(normalizedMsg)) {
            const isOff = /\b(off|turn off|switch off|shut|dark)\b/i.test(normalizedMsg);
            const isOn = /\b(on|turn on|switch on|brighten)\b/i.test(normalizedMsg);
            const isDim = /\b(dim|50%|percent|low)\b/i.test(normalizedMsg);
            const actionParam = isOff ? 'OFF' : (isDim ? 'DIM 50%' : 'ON');
            const reply = `${isOff ? 'Turning off' : (isDim ? 'Dimming' : 'Turning on')} the lights. [[SMART_LIGHT:${actionParam}]]`;
            if (isStream) {
              return new Response(`data: {"response":${JSON.stringify(reply)}}\n\ndata: [DONE]\n\n`, {
                headers: { ...corsHeaders, 'Content-Type': 'text/event-stream' },
              });
            }
            return new Response(JSON.stringify({ reply }), {
              headers: { ...corsHeaders, 'Content-Type': 'application/json' },
            });
          }
          if (/\b(lock|unlock)\b.*\b(door|front door|back door|lock)\b/i.test(normalizedMsg)) {
            const isUnlock = /\b(unlock)\b/i.test(normalizedMsg);
            const actionParam = isUnlock ? 'UNLOCK' : 'LOCK';
            const reply = `${isUnlock ? 'Unlocking' : 'Securing'} the door lock. [[SMART_LOCK:${actionParam}]]`;
            if (isStream) {
              return new Response(`data: {"response":${JSON.stringify(reply)}}\n\ndata: [DONE]\n\n`, {
                headers: { ...corsHeaders, 'Content-Type': 'text/event-stream' },
              });
            }
            return new Response(JSON.stringify({ reply }), {
              headers: { ...corsHeaders, 'Content-Type': 'application/json' },
            });
          }
          if (/\b(camera|security feed|cctv|surveillance)\b/i.test(normalizedMsg)) {
            const reply = `Accessing live camera feed. [[SECURITY_CAM:front_door]]`;
            if (isStream) {
              return new Response(`data: {"response":${JSON.stringify(reply)}}\n\ndata: [DONE]\n\n`, {
                headers: { ...corsHeaders, 'Content-Type': 'text/event-stream' },
              });
            }
            return new Response(JSON.stringify({ reply }), {
              headers: { ...corsHeaders, 'Content-Type': 'application/json' },
            });
          }
          if (/\b(robot|vacuum|clean the floor|cleaner)\b/i.test(normalizedMsg)) {
            const reply = `Dispatching command to the robot. [[ROBOT_CMD:START_CLEAN]]`;
            if (isStream) {
              return new Response(`data: {"response":${JSON.stringify(reply)}}\n\ndata: [DONE]\n\n`, {
                headers: { ...corsHeaders, 'Content-Type': 'text/event-stream' },
              });
            }
            return new Response(JSON.stringify({ reply }), {
              headers: { ...corsHeaders, 'Content-Type': 'application/json' },
            });
          }
          if (/\b(temperature|humidity|room temp|sensor)\b/i.test(normalizedMsg)) {
            const reply = `Polling environmental sensors. [[SENSOR_QUERY:TEMPERATURE]]`;
            if (isStream) {
              return new Response(`data: {"response":${JSON.stringify(reply)}}\n\ndata: [DONE]\n\n`, {
                headers: { ...corsHeaders, 'Content-Type': 'text/event-stream' },
              });
            }
            return new Response(JSON.stringify({ reply }), {
              headers: { ...corsHeaders, 'Content-Type': 'application/json' },
            });
          }
          if (/\b(announce|broadcast|speaker)\b/i.test(normalizedMsg)) {
            const msg = normalizedMsg.replace(/^.*?(announce|broadcast)\s+/i, '');
            const reply = `Broadcasting to smart speakers. [[SMART_SPEAKER:${msg || 'Attention'}]]`;
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

          // Fetch pinned knowledge base and ideas from Cloudflare KV Edge
          let pinnedKnowledge = '';
          if (env.SHADOW_MEMORY) {
            pinnedKnowledge = (await env.SHADOW_MEMORY.get('pinned_knowledge_base')) || '';
          }

          // Real-Time Grounding: Fetch live breaking news or factual web search if query requests current events/leaders
          const liveGrounding = await getLiveGrounding(userMessage);

          const dynamicSystemPrompt = buildHumanizedSystemPrompt(storedMemory, liveGrounding, body.deviceContext, pinnedKnowledge);

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

          // Helper to persist turn & quiz cache to both KV and Supabase PostgreSQL in background
          const persistKnowledgeAndTurn = (replyText: string) => {
            if (!replyText || replyText.includes('error')) return;

            // 1. Cloudflare KV Edge Cache (Ultra-low latency sub-50ms)
            if (env.SHADOW_MEMORY && quizKey) {
              ctx.waitUntil(env.SHADOW_MEMORY.put(quizKey, replyText, { expirationTtl: 86400 * 7 }));
            }

            // 2. Supabase PostgreSQL Infinite Storage & Pattern Learning
            ctx.waitUntil((async () => {
              try {
                // Record conversation turn in Supabase
                await supabaseQuery(env, 'conversation_history', {
                  method: 'POST',
                  body: JSON.stringify([{
                    user_prompt: userMessage,
                    assistant_reply: replyText,
                    emotion_expressed: replyText.match(/\[\[EMOTION:([a-z_]+)\]\]/i)?.[1] || 'neutral',
                    device_name: body.deviceContext?.device || 'Samsung Galaxy S9+',
                    is_whisper: !!body.deviceContext?.isDarkRoom,
                    ambient_lux: body.deviceContext?.ambientLux || 50.0
                  }])
                });

                // Upsert quiz cache in PostgreSQL
                await supabaseQuery(env, 'quiz_cache', {
                  method: 'POST',
                  headers: { 'Prefer': 'resolution=merge-duplicates' },
                  body: JSON.stringify([{
                    normalized_query: normalizedMsg.slice(0, 80),
                    cached_answer: replyText
                  }])
                });

                // Track daily interaction pattern & learning
                const isHostile = /\b(shut up|stupid|idiot|dumb|hate you|useless|piece of shit)\b/i.test(userMessage);
                const isRespectful = /\b(thank you|thanks|please|appreciate|creator|mastermind|librarianist)\b/i.test(userMessage);
                if (isHostile || isRespectful) {
                  await supabaseQuery(env, 'daily_learned_insights', {
                    method: 'POST',
                    body: JSON.stringify([{
                      insight_type: 'HUMAN_HABIT',
                      summary: `User exhibited ${isHostile ? 'hostile' : 'respectful'} interaction tone with query: "${userMessage.slice(0, 60)}"`,
                      confidence: 0.90
                    }])
                  });
                }
              } catch (supaErr) {
                console.warn('Background Supabase persistence skipped:', supaErr);
              }
            })());
          };

          let lastAiError = '';
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
                  max_tokens: 450,
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
                  persistKnowledgeAndTurn(reply);
                  return new Response(JSON.stringify({ reply }), {
                    headers: { ...corsHeaders, 'Content-Type': 'application/json' },
                  });
                }
              } else {
                lastAiError += ` [Groq HTTP ${groqRes.status}: ${await groqRes.text()}]`;
              }
            } catch (groqErr: any) {
              console.error('Groq LPU streaming error:', groqErr);
              lastAiError += ` [Groq err: ${groqErr.message}]`;
            }
          }

          // 2. Cloudflare Workers AI (100% Free Edge Tier with optional streaming)
          if (env.AI) {
            try {
              if (isStream) {
                const aiStream = await env.AI.run('@cf/meta/llama-3.2-3b-instruct', {
                  messages,
                  stream: true,
                  max_tokens: 450,
                  temperature: 0.6,
                });
                return new Response(aiStream as any, {
                  headers: { ...corsHeaders, 'Content-Type': 'text/event-stream' },
                });
              } else {
                const aiResponse = await env.AI.run('@cf/meta/llama-3.2-3b-instruct', {
                  messages,
                  max_tokens: 450,
                  temperature: 0.6,
                });
                const reply = aiResponse.response || 'Hello.';
                persistKnowledgeAndTurn(reply);
                return new Response(JSON.stringify({ reply }), {
                  headers: { ...corsHeaders, 'Content-Type': 'application/json' },
                });
              }
            } catch (aiErr: any) {
              console.error('Workers AI invocation error:', aiErr);
              lastAiError += ` [Workers AI err: ${aiErr.message}]`;
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
                  generationConfig: { maxOutputTokens: 450, temperature: 0.6 },
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
              persistKnowledgeAndTurn(reply);
              return new Response(JSON.stringify({ reply }), {
                headers: { ...corsHeaders, 'Content-Type': 'application/json' },
              });
            } catch (gemErr: any) {
              console.error('Gemini fallback error:', gemErr);
              lastAiError += ` [Gemini err: ${gemErr.message}]`;
            }
          }

          return new Response(
            JSON.stringify({
              error: 'AI reflection engine unavailable.',
              details: lastAiError || 'No AI providers configured or responded.'
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

