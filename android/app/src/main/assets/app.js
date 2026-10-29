// The Menacing Shadow Eyes: 1:1 Pixel-Accurate Animated Eyes from eyes.png
// PURE 0-NIT BLACK SCREEN // SMALL CENTERED EYES ONLY // ZERO CONTENT

const EYE_DATA = {"width": 244, "height": 84, "leftContour": [[-112.66666666666667, -47.68181818181818], [-112.66666666666667, -24.68181818181818], [-111.66666666666667, -20.68181818181818], [-110.66666666666667, -17.68181818181818], [-106.66666666666667, -9.68181818181818], [-102.66666666666667, -4.68181818181818], [-100.66666666666667, -2.68181818181818], [-99.66666666666667, -2.68181818181818], [-96.66666666666667, 0.3181818181818201], [-93.66666666666667, 2.31818181818182], [-89.66666666666667, 4.31818181818182], [-83.66666666666667, 7.31818181818182], [-79.66666666666667, 8.31818181818182], [-75.66666666666667, 9.31818181818182], [-54.66666666666667, 9.31818181818182], [-49.66666666666667, 8.31818181818182], [-46.66666666666667, 7.31818181818182], [-43.66666666666667, 6.31818181818182], [-37.66666666666667, 3.31818181818182], [-32.66666666666667, 0.3181818181818201], [-28.66666666666667, -2.68181818181818], [-24.66666666666667, -6.68181818181818], [-24.66666666666667, -7.68181818181818], [-19.66666666666667, -12.68181818181818], [-16.66666666666667, -18.68181818181818], [-16.66666666666667, -21.68181818181818], [-18.66666666666667, -21.68181818181818], [-22.66666666666667, -23.68181818181818], [-30.66666666666667, -25.68181818181818], [-62.66666666666667, -33.68181818181818], [-73.66666666666667, -37.68181818181818], [-102.66666666666667, -44.68181818181818], [-106.66666666666667, -46.68181818181818], [-109.66666666666667, -46.68181818181818], [-110.66666666666667, -47.68181818181818]], "rightContour": [[30.33333333333333, -11.68181818181818], [30.33333333333333, -6.68181818181818], [31.33333333333333, -3.68181818181818], [32.33333333333333, -0.6818181818181799], [34.33333333333333, 3.31818181818182], [37.33333333333333, 9.31818181818182], [39.33333333333333, 12.31818181818182], [48.33333333333333, 21.31818181818182], [52.33333333333333, 24.31818181818182], [56.33333333333333, 26.31818181818182], [62.33333333333333, 29.31818181818182], [71.33333333333333, 32.31818181818182], [76.33333333333333, 33.31818181818182], [93.33333333333333, 33.31818181818182], [97.33333333333333, 32.31818181818182], [100.33333333333333, 31.31818181818182], [103.33333333333333, 30.31818181818182], [105.33333333333333, 29.31818181818182], [107.33333333333333, 28.31818181818182], [110.33333333333333, 26.31818181818182], [113.33333333333333, 24.31818181818182], [118.33333333333333, 19.31818181818182], [121.33333333333333, 15.31818181818182], [123.33333333333333, 10.31818181818182], [121.33333333333333, 9.31818181818182], [117.33333333333333, 8.31818181818182], [113.33333333333333, 8.31818181818182], [100.33333333333333, 4.31818181818182], [96.33333333333333, 4.31818181818182], [33.33333333333333, -10.68181818181818], [32.33333333333333, -11.68181818181818]], "leftRestPupil": [-48.66666666666667, -13.68181818181818], "rightRestPupil": [94.33333333333333, 10.31818181818182], "leftPupilRadius": 19, "rightPupilRadius": 18};

var canvas = document.getElementById('shadowCanvas');
var ctx = canvas.getContext('2d');

var width = 0;
var height = 0;
var dpr = window.devicePixelRatio || 2.5;
var lastClientW = 0;
var lastClientH = 0;
var eyeScale = 0.22;

function resizeCanvas() {
  dpr = window.devicePixelRatio || 2.5;
  if (dpr < 1.0) dpr = 2.5;
  
  var sw = (window.screen && window.screen.width) ? window.screen.width : 0;
  var sh = (window.screen && window.screen.height) ? window.screen.height : 0;
  
  // Logical CSS dimensions
  var cssW = sw > 0 ? (sw > 600 ? Math.round(sw / dpr) : sw) : (window.innerWidth || 360);
  var cssH = sh > 0 ? (sh > 1000 ? Math.round(sh / dpr) : sh) : (window.innerHeight || 640);
  
  // Fallbacks to standard mobile viewport if unmeasured
  if (cssW < 320 || cssW > 600) cssW = 360;
  if (cssH < 480 || cssH > 1200) cssH = 640;
  
  width = cssW;
  height = cssH;
  
  // Hardware backing store
  canvas.width = Math.round(width * dpr);
  canvas.height = Math.round(height * dpr);
  canvas.style.width = width + 'px';
  canvas.style.height = height + 'px';
  
  // Proportions: ~34% of screen width (e.g. ~122px on 360px portrait, ~140px on 412px)
  var targetEyeWidth = width * 0.34;
  eyeScale = targetEyeWidth / 236;
  console.log("SHADOW_METRICS: width=" + width + " height=" + height + " dpr=" + dpr + " eyeScale=" + eyeScale + " canvasW=" + canvas.width + " canvasH=" + canvas.height);
}

window.addEventListener('resize', resizeCanvas);
window.addEventListener('orientationchange', function() { setTimeout(resizeCanvas, 100); });
document.addEventListener('DOMContentLoaded', resizeCanvas);
resizeCanvas();

// Gaze tracking
var userGaze = { x: width * 0.5, y: height * 0.5 };
var smoothGaze = { x: width * 0.5, y: height * 0.5 };
var saccadeOffset = { x: 0, y: 0 };
var lastSaccadeTime = 0;
var lastUserInteraction = 0;
var nextIdleLookTime = 0;
var idleGazeDirection = { x: 0, y: 0 };

// Blinking state
var isBlinking = false;
var blinkProgress = 0;
var blinkDuration = 200;
var blinkStartTime = 0;
var nextBlinkTime = performance.now() + 2500;

function onPointerMove(e) {
  userGaze.x = e.clientX;
  userGaze.y = e.clientY;
  lastUserInteraction = performance.now();
}

window.addEventListener('pointermove', onPointerMove);
window.addEventListener('touchmove', function(e) {
  if (e.touches.length > 0) {
    userGaze.x = e.touches[0].clientX;
    userGaze.y = e.touches[0].clientY;
    lastUserInteraction = performance.now();
  }
}, { passive: true });

window.addEventListener('pointerdown', function(e) {
  userGaze.x = e.clientX;
  userGaze.y = e.clientY;
  lastUserInteraction = performance.now();
});

// Gyroscope tilt tracking on mobile
if (window.DeviceOrientationEvent) {
  window.addEventListener('deviceorientation', function(e) {
    if (e.gamma !== null && e.beta !== null) {
      var offsetX = (e.gamma / 35) * (width * 0.35);
      var offsetY = ((e.beta - 40) / 35) * (height * 0.25);
      userGaze.x = width * 0.5 + offsetX;
      userGaze.y = height * 0.5 + offsetY;
      lastUserInteraction = performance.now();
    }
  });
}

// ==============================================================================
// LIVING MOUTH & LIP SYNC ANIMATION ENGINE
// ==============================================================================

// Base anatomical mouth positioning relative to centered eye plane (with 3/4 turn tilt)
const MOUTH_BASE = {
  centerX: 8,       // Offset to match 3/4 face tilt of eyes
  centerY: 82,      // Anatomically positioned below centered eyes
  width: 96,        // Rest width (small big proportion)
  tiltAngle: 0.08   // Slight natural slant matching the eyes
};

// Current smoothed animated state
var mouthState = {
  openness: 0.0,       // 0.0 (closed) to 1.0 (wide jaw drop)
  widthRatio: 1.0,     // 0.6 (pucker) to 1.45 (wide smile)
  cornerLeftY: 0.0,    // Left corner vertical offset (smirk/frown)
  cornerRightY: 0.0,   // Right corner vertical offset
  teethVisible: 0.0,   // 0.0 to 1.0 (teeth row visibility)
  smile: 0.05,         // Base resting curvature
  pucker: 0.0
};

// Target animation state
var mouthTarget = {
  openness: 0.0,
  widthRatio: 1.0,
  cornerLeftY: 0.0,
  cornerRightY: 0.0,
  teethVisible: 0.0,
  smile: 0.05,
  pucker: 0.0
};

// Idle natural expressions
var IDLE_MOUTH_EXPRESSIONS = [
  { name: 'neutral',        openness: 0.0,  widthRatio: 1.00, cornerLeftY: 0,  cornerRightY: 0,  teethVisible: 0.0,  smile: 0.04 },
  { name: 'smirk_right',    openness: 0.04, widthRatio: 1.08, cornerLeftY: -1, cornerRightY: -6, teethVisible: 0.15, smile: 0.14 },
  { name: 'smirk_left',     openness: 0.03, widthRatio: 1.05, cornerLeftY: -5, cornerRightY: 0,  teethVisible: 0.10, smile: 0.11 },
  { name: 'predator_grin',  openness: 0.18, widthRatio: 1.22, cornerLeftY: -6, cornerRightY: -7, teethVisible: 0.60, smile: 0.24 },
  { name: 'slight_part',    openness: 0.08, widthRatio: 0.98, cornerLeftY: 0,  cornerRightY: 0,  teethVisible: 0.25, smile: 0.02 },
  { name: 'pensive',        openness: 0.0,  widthRatio: 0.94, cornerLeftY: 2,  cornerRightY: 1,  teethVisible: 0.0,  smile: -0.04 }
];

var nextIdleMouthTime = 0;

// Standard Viseme Phoneme Mappings for Lip Sync (Wide, clear aperture)
const VISEMES = {
  REST: { openness: 0.00, widthRatio: 1.00, cornerLeftY: 0,  cornerRightY: 0,  teethVisible: 0.00, smile: 0.04 },
  AH:   { openness: 0.95, widthRatio: 1.20, cornerLeftY: -4, cornerRightY: -4, teethVisible: 0.50, smile: 0.16 },
  EE:   { openness: 0.58, widthRatio: 1.40, cornerLeftY: -7, cornerRightY: -7, teethVisible: 0.95, smile: 0.28 },
  IH:   { openness: 0.68, widthRatio: 1.26, cornerLeftY: -5, cornerRightY: -5, teethVisible: 0.75, smile: 0.20 },
  OH:   { openness: 0.92, widthRatio: 0.96, cornerLeftY: 0,  cornerRightY: 0,  teethVisible: 0.25, smile: 0.04 },
  OO:   { openness: 0.55, widthRatio: 0.80, cornerLeftY: 0,  cornerRightY: 0,  teethVisible: 0.12, smile: 0.02 },
  BMP:  { openness: 0.00, widthRatio: 1.02, cornerLeftY: 1,  cornerRightY: 1,  teethVisible: 0.00, smile: 0.03 },
  FV:   { openness: 0.38, widthRatio: 1.18, cornerLeftY: -3, cornerRightY: -3, teethVisible: 0.70, smile: 0.10 },
  SZ:   { openness: 0.42, widthRatio: 1.38, cornerLeftY: -6, cornerRightY: -6, teethVisible: 0.95, smile: 0.24 },
  TDN:  { openness: 0.60, widthRatio: 1.22, cornerLeftY: -5, cornerRightY: -5, teethVisible: 0.72, smile: 0.14 }
};

// Lip Sync Speech Queue & State
var speechTimeline = [];
var speechStartTime = 0;
var isSpeaking = false;
var speechEndCallback = null;

function textToVisemes(text) {
  var words = text.toLowerCase().split(/\s+/);
  var timeline = [];
  var speedFactor = 0.82; // Faster 1.18x speech synchronization
  
  for (var w = 0; w < words.length; w++) {
    var word = words[w].replace(/[^a-z]/g, '');
    if (!word) continue;
    
    var i = 0;
    while (i < word.length) {
      var two = word.slice(i, i + 2);
      var one = word[i];
      var vis = 'AH';
      var dur = 95;
      var advance = 1;

      if (two === 'ee' || two === 'ea' || two === 'ey' || two === 'ie') {
        vis = 'EE'; dur = 120; advance = 2;
      } else if (two === 'oo' || two === 'ou' || two === 'ew') {
        vis = 'OO'; dur = 125; advance = 2;
      } else if (two === 'oh' || two === 'ow' || two === 'oa') {
        vis = 'OH'; dur = 120; advance = 2;
      } else if (two === 'sh' || two === 'ch' || two === 'th') {
        vis = 'SZ'; dur = 95; advance = 2;
      } else if (two === 'wh') {
        vis = 'OO'; dur = 85; advance = 2;
      } else if (one === 'a') {
        vis = 'AH'; dur = 100;
      } else if (one === 'e') {
        vis = 'EE'; dur = 90;
      } else if (one === 'i' || one === 'y') {
        vis = 'IH'; dur = 95;
      } else if (one === 'o') {
        vis = 'OH'; dur = 105;
      } else if (one === 'u') {
        vis = 'OO'; dur = 95;
      } else if (one === 'm' || one === 'b' || one === 'p') {
        vis = 'BMP'; dur = 80;
      } else if (one === 'f' || one === 'v') {
        vis = 'FV'; dur = 85;
      } else if (one === 's' || one === 'z' || one === 'x') {
        vis = 'SZ'; dur = 90;
      } else if (one === 't' || one === 'd' || one === 'n' || one === 'l' || one === 'r') {
        vis = 'TDN'; dur = 80;
      } else if (one === 'w') {
        vis = 'OO'; dur = 80;
      } else {
        vis = 'TDN'; dur = 75;
      }

      timeline.push({ viseme: vis, duration: Math.round(dur * speedFactor) });
      i += advance;
    }

    timeline.push({ viseme: 'REST', duration: Math.round(30 * speedFactor) });
  }

  timeline.push({ viseme: 'REST', duration: 120 });
  return timeline;
}

function updateSpeechTimeline(now) {
  if (!isSpeaking || speechTimeline.length === 0) return;
  
  var elapsed = now - speechStartTime;
  var accumulated = 0;
  var currentToken = null;
  
  for (var i = 0; i < speechTimeline.length; i++) {
    accumulated += speechTimeline[i].duration;
    if (elapsed < accumulated) {
      currentToken = speechTimeline[i];
      break;
    }
  }
  
  if (currentToken) {
    var v = VISEMES[currentToken.viseme] || VISEMES.REST;
    mouthTarget.openness = v.openness;
    mouthTarget.widthRatio = v.widthRatio;
    mouthTarget.cornerLeftY = v.cornerLeftY;
    mouthTarget.cornerRightY = v.cornerRightY;
    mouthTarget.teethVisible = v.teethVisible;
    mouthTarget.smile = v.smile;
  } else {
    isSpeaking = false;
    speechTimeline = [];
    mouthTarget.openness = 0.0;
    mouthTarget.teethVisible = 0.0;
    lastAiSpeechFinishedTime = Date.now();
    if (typeof speechEndCallback === 'function') {
      var cb = speechEndCallback;
      speechEndCallback = null;
      cb();
    }
  }
}

// Cloudflare Edge Brain
const CLOUDFLARE_AI_ENDPOINT = 'https://librarianist.xyz/api/chat';

async function queryCloudflareAi(promptMessage) {
  try {
    var resp = await fetch(CLOUDFLARE_AI_ENDPOINT, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ message: promptMessage })
    });
    if (resp.ok) {
      var json = await resp.json();
      if (json && json.reply) {
        return json.reply;
      }
    }
  } catch (e) {
    console.warn('Cloudflare Edge offline:', e);
  }
  return null;
}

// Keep screen awake (OLED desk sentinel mode)
async function requestScreenLock() {
  try {
    if ('wakeLock' in navigator) {
      await navigator.wakeLock.request('screen');
    }
  } catch (err) {}
}
document.addEventListener('click', requestScreenLock, { once: true });
document.addEventListener('touchstart', requestScreenLock, { once: true });

// Idle glance directions
var IDLE_LOOK_DIRECTIONS = [
  { x: 0, y: 0 },         // Direct eye contact
  { x: 0.8, y: -0.3 },    // Glancing top-right
  { x: -0.8, y: -0.3 },   // Glancing top-left
  { x: 0.9, y: 0.15 },    // Looking right
  { x: -0.9, y: 0.15 },   // Looking left
  { x: 0, y: 0.6 },       // Looking down
  { x: 0, y: 0 }          // Direct eye contact
];

function pickIdleGlance() {
  var chosen = IDLE_LOOK_DIRECTIONS[Math.floor(Math.random() * IDLE_LOOK_DIRECTIONS.length)];
  idleGazeDirection.x = chosen.x;
  idleGazeDirection.y = chosen.y;
}

function triggerBlink() {
  if (isBlinking) return;
  isBlinking = true;
  blinkStartTime = performance.now();
  blinkDuration = 180 + Math.random() * 80;
}

// Render Loop
function render(now) {
  if (width <= 0 || height <= 0 || canvas.width <= 0 || canvas.height <= 0) {
    resizeCanvas();
  }

  // 4K HiDPI context scaling
  ctx.save();
  ctx.scale(dpr, dpr);

  // 1. Pure 0-nit black clear
  ctx.fillStyle = '#000000';
  ctx.fillRect(0, 0, width, height);

  // EYES ARE FIXED EXACT DEAD CENTER
  var curCenterX = width * 0.5;
  var curCenterY = height * 0.5;

  // 2. Autonomous Glances when Idle
  var isIdle = now - lastUserInteraction > 2500;
  if (isIdle) {
    if (now > nextIdleLookTime) {
      pickIdleGlance();
      nextIdleLookTime = now + 2400 + Math.random() * 3200;
      if (Math.random() < 0.4) triggerBlink();
    }
    var targetIdleX = curCenterX + idleGazeDirection.x * (width * 0.35);
    var targetIdleY = curCenterY + idleGazeDirection.y * (height * 0.25);
    userGaze.x += (targetIdleX - userGaze.x) * 0.05;
    userGaze.y += (targetIdleY - userGaze.y) * 0.05;
  }

  // Autonomous Idle Mouth Expressions
  if (isIdle && !isSpeaking) {
    if (now > nextIdleMouthTime) {
      var chosenMouth = IDLE_MOUTH_EXPRESSIONS[Math.floor(Math.random() * IDLE_MOUTH_EXPRESSIONS.length)];
      mouthTarget.openness = chosenMouth.openness;
      mouthTarget.widthRatio = chosenMouth.widthRatio;
      mouthTarget.cornerLeftY = chosenMouth.cornerLeftY;
      mouthTarget.cornerRightY = chosenMouth.cornerRightY;
      mouthTarget.teethVisible = chosenMouth.teethVisible;
      mouthTarget.smile = chosenMouth.smile;
      nextIdleMouthTime = now + 3500 + Math.random() * 4500;
    }
  }

  // Update Lip Sync Speech Timeline
  updateSpeechTimeline(now);

  // Smooth spring interpolation for mouth animation
  var mouthEase = isSpeaking ? 0.38 : 0.12;
  mouthState.openness += (mouthTarget.openness - mouthState.openness) * mouthEase;
  mouthState.widthRatio += (mouthTarget.widthRatio - mouthState.widthRatio) * mouthEase;
  mouthState.cornerLeftY += (mouthTarget.cornerLeftY - mouthState.cornerLeftY) * mouthEase;
  mouthState.cornerRightY += (mouthTarget.cornerRightY - mouthState.cornerRightY) * mouthEase;
  mouthState.teethVisible += (mouthTarget.teethVisible - mouthState.teethVisible) * mouthEase;
  mouthState.smile += (mouthTarget.smile - mouthState.smile) * mouthEase;

  // 3. Micro-saccades
  if (now - lastSaccadeTime > 1400 + Math.random() * 2200) {
    saccadeOffset.x = (Math.random() - 0.5) * 5;
    saccadeOffset.y = (Math.random() - 0.5) * 3;
    lastSaccadeTime = now;
  }

  // Smooth Gaze tracking
  smoothGaze.x += (userGaze.x + saccadeOffset.x - smoothGaze.x) * 0.09;
  smoothGaze.y += (userGaze.y + saccadeOffset.y - smoothGaze.y) * 0.09;

  // 4. Autonomous Blinking Logic
  if (now > nextBlinkTime && !isBlinking) {
    triggerBlink();
    var doubleBlink = Math.random() < 0.2;
    nextBlinkTime = now + (doubleBlink ? 320 : 3500 + Math.random() * 3500);
  }

  if (isBlinking) {
    var elapsed = now - blinkStartTime;
    var progress = Math.min(1, elapsed / blinkDuration);

    if (progress < 0.45) {
      blinkProgress = progress / 0.45;
    } else {
      blinkProgress = 1 - (progress - 0.45) / 0.55;
    }

    if (progress >= 1) {
      isBlinking = false;
      blinkProgress = 0;
    }
  }

  // 5. Draw the Living Eyes
  ctx.save();
  ctx.translate(curCenterX, curCenterY);
  ctx.scale(eyeScale, eyeScale);

  var gazeDx = (smoothGaze.x - curCenterX) / (width * 0.5);
  var gazeDy = (smoothGaze.y - curCenterY) / (height * 0.5);

  // Left Eye
  drawSingleEye(
    EYE_DATA.leftContour,
    EYE_DATA.leftRestPupil,
    EYE_DATA.leftPupilRadius,
    gazeDx,
    gazeDy,
    blinkProgress
  );

  // Right Eye
  drawSingleEye(
    EYE_DATA.rightContour,
    EYE_DATA.rightRestPupil,
    EYE_DATA.rightPupilRadius,
    gazeDx,
    gazeDy,
    blinkProgress
  );

  // Living Expressive & Lip-Sync Mouth
  drawMouth(now, gazeDx, gazeDy);

  ctx.restore(); // restore eyeScale transform
  ctx.restore(); // restore dpr transform

  requestAnimationFrame(render);
}

function drawSingleEye(contour, restPupil, pupilRadius, gazeDx, gazeDy, blink) {
  ctx.save();

  // 1. Build Sclera Path
  ctx.beginPath();
  ctx.moveTo(contour[0][0], contour[0][1]);
  for (var i = 1; i < contour.length; i++) {
    ctx.lineTo(contour[i][0], contour[i][1]);
  }
  ctx.closePath();

  // Pure White Sclera
  ctx.fillStyle = '#ffffff';
  ctx.fill();

  // Clip inside eye sclera
  ctx.clip();

  // 2. Dynamic Pupil Position
  var maxTravelX = 14;
  var maxTravelY = 10;
  var pupilX = restPupil[0] + Math.max(-maxTravelX, Math.min(maxTravelX, gazeDx * maxTravelX));
  var pupilY = restPupil[1] + Math.max(-maxTravelY, Math.min(maxTravelY, gazeDy * maxTravelY));

  // Solid Black Pupil
  ctx.beginPath();
  ctx.arc(pupilX, pupilY, pupilRadius, 0, Math.PI * 2);
  ctx.fillStyle = '#000000';
  ctx.fill();

  // Specular Catchlight
  ctx.beginPath();
  ctx.arc(pupilX + pupilRadius * 0.35, pupilY - pupilRadius * 0.25, 2.8, 0, Math.PI * 2);
  ctx.fillStyle = '#ffffff';
  ctx.fill();

  // 3. Eyelid Drop Shadow (Ominous depth under upper lid)
  var minY = 999;
  var maxY = -999;
  var minX = 999;
  var maxX = -999;
  for (var i = 0; i < contour.length; i++) {
    var p = contour[i];
    if (p[0] < minX) minX = p[0];
    if (p[0] > maxX) maxX = p[0];
    if (p[1] < minY) minY = p[1];
    if (p[1] > maxY) maxY = p[1];
  }

  var shadowGrad = ctx.createLinearGradient(0, minY, 0, minY + 18);
  shadowGrad.addColorStop(0, 'rgba(0, 0, 0, 0.9)');
  shadowGrad.addColorStop(0.6, 'rgba(0, 0, 0, 0.35)');
  shadowGrad.addColorStop(1, 'rgba(0, 0, 0, 0)');
  ctx.fillStyle = shadowGrad;
  ctx.fillRect(minX - 5, minY - 5, (maxX - minX) + 10, 24);

  // 4. Blinking Eyelid (Lowers smoothly from the top)
  if (blink > 0.01) {
    var currentLidY = minY + (maxY - minY + 10) * blink;
    ctx.fillStyle = '#000000';
    ctx.fillRect(minX - 10, minY - 10, (maxX - minX) + 20, currentLidY - minY + 10);
  }

  ctx.restore();
}

// ==============================================================================
// DRAW ANIMATED-STYLE LIVING MOUTH (ANIME/MANGA STYLING & LIP SYNC)
// ==============================================================================
function drawMouth(now, gazeDx, gazeDy) {
  // Natural 3D head perspective shift
  var cx = MOUTH_BASE.centerX + gazeDx * 7;
  var cy = MOUTH_BASE.centerY + gazeDy * 4 + Math.sin(now * 0.002) * 0.7; // breathing oscillation
  
  // Widen mouth when opening/speaking so lips open well and a bit wide
  var talkWiden = isSpeaking ? (1.0 + mouthState.openness * 0.22) : 1.0;
  var w = MOUTH_BASE.width * mouthState.widthRatio * talkWiden;
  var halfW = w * 0.5;
  var tilt = MOUTH_BASE.tiltAngle;
  
  // Left and Right corners (following 3/4 turn plane)
  var lx = cx - halfW * Math.cos(tilt);
  var ly = cy - halfW * Math.sin(tilt) + mouthState.cornerLeftY;
  var rx = cx + halfW * Math.cos(tilt);
  var ry = cy + halfW * Math.sin(tilt) + mouthState.cornerRightY;
  
  var openness = mouthState.openness;
  var smile = mouthState.smile;
  
  // Upper lip apex (with stylized Cupid's bow contour, lifts slightly when vocalizing)
  var midUpperX = cx + (rx - lx) * 0.04;
  var midUpperY = (ly + ry) * 0.5 - smile * 14 - openness * 10;
  
  // Lower lip apex (deeper drop so opening is well-defined and clearly visible)
  var lowerDrop = openness * 48 + Math.abs(smile) * 8;
  var midLowerX = cx;
  var midLowerY = (ly + ry) * 0.5 + lowerDrop;
  
  ctx.save();
  
  if (openness > 0.06) {
    // ========================================================================
    // 1. OPEN ANIMATED MOUTH (TALKING PHONEMES / SERRATED VILLAIN GRIN)
    // ========================================================================
    ctx.beginPath();
    // Upper lip curve
    ctx.moveTo(lx, ly);
    ctx.quadraticCurveTo((lx + midUpperX) * 0.5, ly + (midUpperY - ly) * 0.72, midUpperX, midUpperY);
    ctx.quadraticCurveTo((midUpperX + rx) * 0.5, ry + (midUpperY - ry) * 0.72, rx, ry);
    
    // Lower lip wedge / curve
    ctx.quadraticCurveTo((rx + midLowerX) * 0.5, ry + (midLowerY - ry) * 0.88, midLowerX, midLowerY);
    ctx.quadraticCurveTo((midLowerX + lx) * 0.5, ly + (midLowerY - ly) * 0.88, lx, ly);
    ctx.closePath();
    
    // Deep dark anime oral cavity (#160206)
    ctx.fillStyle = '#160206';
    ctx.fill();
    
    // Clip details inside the mouth
    ctx.save();
    ctx.clip();
    
    // --- STYLIZED ANIME TONGUE ---
    if (openness > 0.18) {
      var tongueH = lowerDrop * 0.44;
      var tongueW = w * 0.68;
      ctx.fillStyle = '#b5283c';
      ctx.beginPath();
      ctx.moveTo(cx - tongueW * 0.5, midLowerY + 2);
      ctx.quadraticCurveTo(cx, midLowerY - tongueH, cx + tongueW * 0.5, midLowerY + 2);
      ctx.closePath();
      ctx.fill();
    }
    
    // --- STYLIZED ANIME UPPER TEETH & CANINE FANGS ---
    if (mouthState.teethVisible > 0.08) {
      var teethH = Math.min(mouthState.teethVisible * 9.5 + 3, lowerDrop * 0.58);
      var fangLeftX = lx + (midUpperX - lx) * 0.32;
      var fangRightX = midUpperX + (rx - midUpperX) * 0.68;
      
      ctx.fillStyle = '#ffffff';
      ctx.beginPath();
      // Upper lip border
      ctx.moveTo(lx - 2, ly);
      ctx.quadraticCurveTo((lx + midUpperX) * 0.5, ly + (midUpperY - ly) * 0.72, midUpperX, midUpperY);
      ctx.quadraticCurveTo((midUpperX + rx) * 0.5, ry + (midUpperY - ry) * 0.72, rx + 2, ry);
      
      // Bottom edge of upper teeth featuring sharp stylized canine fangs
      ctx.lineTo(rx - 2, ry + 2);
      ctx.lineTo(fangRightX, ry + teethH * 1.45); // sharp right fang!
      ctx.lineTo(midUpperX + 5, midUpperY + teethH * 0.85);
      ctx.lineTo(midUpperX - 5, midUpperY + teethH * 0.85);
      ctx.lineTo(fangLeftX, ly + teethH * 1.45);  // sharp left fang!
      ctx.lineTo(lx + 2, ly + 2);
      ctx.closePath();
      ctx.fill();
      
      // Crisp center incisor division
      ctx.strokeStyle = '#000000';
      ctx.lineWidth = 1.1;
      ctx.beginPath();
      ctx.moveTo(midUpperX, midUpperY);
      ctx.lineTo(midUpperX, midUpperY + teethH * 0.75);
      ctx.stroke();
    }
    
    // --- STYLIZED LOWER TEETH ON WIDE EXPRESSIONS (EE / SZ / TDN) ---
    if (openness > 0.30 && mouthState.teethVisible > 0.30) {
      var lowTeethH = Math.min((openness - 0.20) * 7.0, 6.0);
      ctx.fillStyle = '#ffffff';
      ctx.beginPath();
      ctx.moveTo(lx + 8, ly + lowerDrop * 0.82);
      ctx.quadraticCurveTo(cx, midLowerY - lowTeethH, rx - 8, ry + lowerDrop * 0.82);
      ctx.quadraticCurveTo(cx, midLowerY + 2, lx + 8, ly + lowerDrop * 0.82);
      ctx.closePath();
      ctx.fill();
    }
    
    ctx.restore(); // end clip
    
    // Crisp bold white anime outline
    ctx.strokeStyle = '#ffffff';
    ctx.lineWidth = 3.2;
    ctx.lineCap = 'round';
    ctx.lineJoin = 'round';
    ctx.stroke();
    
  } else {
    // ========================================================================
    // 2. CLOSED ANIMATED MOUTH (ANIME SMIRK / SHARP CANINE FANG PEEK)
    // ========================================================================
    ctx.strokeStyle = '#ffffff';
    ctx.lineWidth = 2.6;
    ctx.lineCap = 'round';
    ctx.lineJoin = 'round';
    
    // Expressive stylized lip line with Cupid's dip
    ctx.beginPath();
    ctx.moveTo(lx, ly);
    ctx.quadraticCurveTo((lx + midUpperX) * 0.5, ly + (midUpperY - ly) * 0.68, midUpperX, midUpperY);
    ctx.quadraticCurveTo((midUpperX + rx) * 0.5, ry + (midUpperY - ry) * 0.68, rx, ry);
    ctx.stroke();
    
    // Iconic Anime Villain Fang Peek on Smirk
    var isRightSmirk = mouthState.cornerRightY < -3 || smile > 0.12;
    var isLeftSmirk = mouthState.cornerLeftY < -3;
    
    if (isRightSmirk) {
      // Stylized sharp white fang peeking under right corner
      ctx.fillStyle = '#ffffff';
      ctx.beginPath();
      ctx.moveTo(rx - 12, ry + 0.5);
      ctx.lineTo(rx - 4, ry + 1);
      ctx.lineTo(rx - 8, ry + 6); // fang point
      ctx.closePath();
      ctx.fill();
    } else if (isLeftSmirk) {
      // Stylized sharp white fang peeking under left corner
      ctx.fillStyle = '#ffffff';
      ctx.beginPath();
      ctx.moveTo(lx + 4, ly + 1);
      ctx.lineTo(lx + 12, ly + 0.5);
      ctx.lineTo(lx + 8, ly + 6); // fang point
      ctx.closePath();
      ctx.fill();
    }
    
    // Signature Anime Lower Lip Accent Tick
    var underLipW = 14;
    var underLipY = (ly + ry) * 0.5 + 7.5 + Math.abs(smile) * 2;
    ctx.strokeStyle = 'rgba(255, 255, 255, 0.65)';
    ctx.lineWidth = 1.8;
    ctx.beginPath();
    ctx.moveTo(cx - underLipW * 0.5, underLipY);
    ctx.lineTo(cx + underLipW * 0.5, underLipY);
    ctx.stroke();
  }
  
  // ==========================================================================
  // 3. SIGNATURE ANIME CORNER CREASE ACCENT TICKS
  // ==========================================================================
  ctx.strokeStyle = '#ffffff';
  ctx.lineWidth = 2.0;
  ctx.lineCap = 'round';
  
  // Left corner tick
  var leftCreaseAngle = -0.48 + (mouthState.cornerLeftY * 0.05);
  ctx.beginPath();
  ctx.moveTo(lx, ly);
  ctx.lineTo(lx - 4.5 * Math.cos(leftCreaseAngle), ly + 4.5 * Math.sin(leftCreaseAngle));
  ctx.stroke();
  
  // Right corner tick
  var rightCreaseAngle = 0.48 + (mouthState.cornerRightY * 0.05);
  ctx.beginPath();
  ctx.moveTo(rx, ry);
  ctx.lineTo(rx + 4.5 * Math.cos(rightCreaseAngle), ry - 4.5 * Math.sin(rightCreaseAngle));
  ctx.stroke();
  
  ctx.restore();
}

// ==============================================================================
// PUBLIC SHADOW MOUTH & REALTIME STREAMING CONVERSATION API
// ==============================================================================

// Persistent Multi-Turn Conversation Memory Storage
var HISTORY_STORAGE_KEY = 'shadow_conversation_history_v1';

function loadPersistedHistory() {
  try {
    var raw = localStorage.getItem(HISTORY_STORAGE_KEY);
    if (raw) {
      var parsed = JSON.parse(raw);
      if (Array.isArray(parsed)) {
        return parsed.slice(-24);
      }
    }
  } catch(e) {}
  return [];
}

function persistHistory(history) {
  try {
    localStorage.setItem(HISTORY_STORAGE_KEY, JSON.stringify(history.slice(-24)));
  } catch(e) {}
}

var conversationHistory = loadPersistedHistory();
var clauseQueue = [];
var isProcessingQueue = false;
var isAwaitingResponse = false;
var isNativeSpeaking = false;
var lastAiSpeechFinishedTime = 0;

// Bridge callback from Android Native TTS
window.onNativeSpeechState = function(speaking) {
  isNativeSpeaking = speaking;
  if (speaking) {
    pauseMicrophone();
  } else {
    lastAiSpeechFinishedTime = Date.now();
    if (!isProcessingQueue && !isSpeaking) {
      resumeMicrophone(800);
    }
  }
};

// Stop speaking immediately (Barge-in / User Tap Interruption)
function stopSpeaking() {
  isSpeaking = false;
  speechTimeline = [];
  clauseQueue = [];
  isProcessingQueue = false;
  isAwaitingResponse = false;
  lastAiSpeechFinishedTime = Date.now();
  
  if (window.ShadowNative && typeof window.ShadowNative.stopSpeaking === 'function') {
    window.ShadowNative.stopSpeaking();
  } else if ('speechSynthesis' in window) {
    try { window.speechSynthesis.cancel(); } catch(e) {}
  }
  
  // Snap mouth back to attentive listening posture
  mouthTarget.openness = 0.0;
  mouthTarget.teethVisible = 0.0;
  mouthTarget.smile = 0.04;
  mouthTarget.cornerRightY = 0;
  mouthTarget.cornerLeftY = 0;

  resumeMicrophone(400);
}

// Sanitize text and execute hardware/app action tags
function sanitizeSpeechText(text) {
  if (!text) return '';

  var actionRegex = /\[\[([A-Z_]+)(?::\s*([^\]]*))?\]\]/gi;
  var match;
  while ((match = actionRegex.exec(text)) !== null) {
    var actionType = (match[1] || '').trim().toUpperCase();
    var payload = (match[2] || '').trim();

    console.log('[SHADOW ACTION DISPATCH]:', actionType, payload);

    if (window.ShadowNative) {
      if (typeof window.ShadowNative.executeAction === 'function') {
        window.ShadowNative.executeAction(actionType, payload);
      } else if (actionType === 'OPEN_APP' && typeof window.ShadowNative.launchApp === 'function') {
        window.ShadowNative.launchApp(payload);
      }
    }
  }

  return text.replace(/\[\[[A-Z_]+(?::[^\]]*)?\]\]/gi, '').trim();
}

// Queue clauses and speak them with zero delay (clause-by-clause low-latency streaming)
function enqueueSpeechClause(clause) {
  var clean = sanitizeSpeechText(clause);
  if (!clean) return;
  clauseQueue.push(clean);
  if (!isProcessingQueue && !isSpeaking) {
    processNextClause();
  }
}

function processNextClause() {
  if (clauseQueue.length === 0) {
    isProcessingQueue = false;
    lastAiSpeechFinishedTime = Date.now();
    var nativeActive = window.ShadowNative && typeof window.ShadowNative.isSpeakingNative === 'function' && window.ShadowNative.isSpeakingNative();
    if (!isNativeSpeaking && !nativeActive) {
      resumeMicrophone(800);
    }
    return;
  }
  isProcessingQueue = true;
  var nextClause = clauseQueue.shift();
  window.ShadowMouth.speak(nextClause, function() {
    processNextClause();
  });
}

// Real-time Low-Latency Streaming from Cloudflare Edge
async function streamConversation(userPrompt) {
  if (!userPrompt || !userPrompt.trim()) return;
  
  pauseMicrophone();
  isAwaitingResponse = true;

  // Direct fast local intent: Open/Launch application ONLY for pure, standalone app requests
  var hasCompoundIntent = /\b(and|then|play|pause|stop|skip|next|prev|track|song|music|take|snap|picture|photo|record|video|cam|search|find|lookup|youtube|timer|alarm|torch|flashlight|volume|louder|mute|call|dial|navigate|map|directions)\b/i.test(userPrompt);
  if (!hasCompoundIntent) {
    var openMatch = userPrompt.match(/^(?:can you\s+)?(?:please\s+)?(?:open|launch|start|run|go to)\s+(?:the\s+)?([a-z0-9\s._-]+?)(?:\s+(?:app|application|for me))?[.!?]?$/i);
    if (openMatch && openMatch[1]) {
      var targetApp = openMatch[1].trim();
      if (window.ShadowNative && typeof window.ShadowNative.launchApp === 'function') {
        var launched = window.ShadowNative.launchApp(targetApp);
        if (launched) {
          console.log('[DEVICE COMMAND]: Successfully launched ' + targetApp);
          var replyText = "Opening " + targetApp + ".";
          conversationHistory.push({ role: 'user', content: userPrompt });
          conversationHistory.push({ role: 'assistant', content: replyText });
          enqueueSpeechClause(replyText);
          isAwaitingResponse = false;
          return;
        }
      }
    }
  }

  // Memory reset intent
  var cleanPrompt = userPrompt.trim();
  if (/^(?:clear\s+(?:history|memory)|forget\s+(?:everything|all|our conversation|what i said))/i.test(cleanPrompt)) {
    conversationHistory = [];
    persistHistory(conversationHistory);
    var resetText = "Memory cleared. We start fresh.";
    enqueueSpeechClause(resetText);
    isAwaitingResponse = false;
    return;
  }

  // Record user turn in memory and persist
  conversationHistory.push({ role: 'user', content: cleanPrompt });
  if (conversationHistory.length > 30) {
    conversationHistory = conversationHistory.slice(-30);
  }
  persistHistory(conversationHistory);

  try {
    var resp = await fetch(CLOUDFLARE_AI_ENDPOINT, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Accept': 'text/event-stream'
      },
      body: JSON.stringify({
        message: cleanPrompt,
        history: conversationHistory.slice(0, -1).slice(-16),
        stream: true
      })
    });
    
    if (!resp.ok || !resp.body) {
      var fallback = await queryCloudflareAi(cleanPrompt);
      if (fallback) {
        enqueueSpeechClause(fallback);
        conversationHistory.push({ role: 'assistant', content: fallback });
        persistHistory(conversationHistory);
      } else {
        resumeMicrophone(500);
      }
      return;
    }
    
    var reader = resp.body.getReader();
    var decoder = new TextDecoder();
    var buffer = '';
    var fullReply = '';
    var activeClause = '';
    
    while (true) {
      var chunk = await reader.read();
      if (chunk.done) break;
      
      buffer += decoder.decode(chunk.value, { stream: true });
      var lines = buffer.split('\n');
      buffer = lines.pop() || '';
      
      for (var l = 0; l < lines.length; l++) {
        var line = lines[l].trim();
        if (!line.startsWith('data:')) continue;
        var dataStr = line.replace(/^data:\s*/, '').trim();
        if (dataStr === '[DONE]') continue;
        
        try {
          var parsed = JSON.parse(dataStr);
          var token = '';
          if (parsed.response) {
            token = parsed.response;
          } else if (parsed.choices && parsed.choices[0] && parsed.choices[0].delta && parsed.choices[0].delta.content) {
            token = parsed.choices[0].delta.content;
          }
          
          if (token) {
            fullReply += token;
            activeClause += token;
            
            // Natural sentence boundary: period, question mark, exclamation, or natural length
            var isSentenceTerminator = /[.!?\n]/.test(token);
            var isLengthyClause = activeClause.trim().split(/\s+/).length >= 10;
            
            if (isSentenceTerminator || isLengthyClause) {
              enqueueSpeechClause(activeClause);
              activeClause = '';
            }
          }
        } catch(e) {}
      }
    }
    
    if (activeClause.trim()) {
      enqueueSpeechClause(activeClause);
    }
    
    if (fullReply.trim()) {
      conversationHistory.push({ role: 'assistant', content: fullReply.trim() });
      persistHistory(conversationHistory);
    }
    
  } catch (err) {
    console.warn('Streaming error:', err);
    var errReply = await queryCloudflareAi(cleanPrompt);
    if (errReply) {
      enqueueSpeechClause(errReply);
      conversationHistory.push({ role: 'assistant', content: errReply });
      persistHistory(conversationHistory);
    } else {
      resumeMicrophone(500);
    }
  } finally {
    isAwaitingResponse = false;
  }
}

// High-Precision Voice Activity Detection (VAD) Controller
var speechRecognition = null;
var isRecognitionRunning = false;
var recognitionDesired = true;
var speechDebounceTimer = null;
var activeTranscript = '';

function commitUserSpeech() {
  if (speechDebounceTimer) {
    clearTimeout(speechDebounceTimer);
    speechDebounceTimer = null;
  }

  var text = (activeTranscript || '').trim();
  activeTranscript = '';

  if (text.length > 0) {
    console.log('[EXACT USER SPEECH]:', text);
    pauseMicrophone();
    streamConversation(text);
  }
}

function pauseMicrophone() {
  recognitionDesired = false;
  if (speechDebounceTimer) {
    clearTimeout(speechDebounceTimer);
    speechDebounceTimer = null;
  }
  activeTranscript = '';
  if (speechRecognition) {
    try {
      speechRecognition.abort();
    } catch(e) {}
  }
}

function resumeMicrophone(delayMs) {
  recognitionDesired = true;
  var delay = delayMs !== undefined ? delayMs : 500;
  setTimeout(function() {
    if (!recognitionDesired) return;
    var nativeActive = window.ShadowNative && typeof window.ShadowNative.isSpeakingNative === 'function' && window.ShadowNative.isSpeakingNative();
    if (isSpeaking || isProcessingQueue || isNativeSpeaking || nativeActive || isAwaitingResponse) {
      return;
    }
    startMicrophone();
  }, delay);
}

window.resumeListening = function() {
  resumeMicrophone(250);
};

function startMicrophone() {
  if (!recognitionDesired) return;
  if (speechRecognition) {
    try { speechRecognition.abort(); } catch(e) {}
    speechRecognition = null;
  }
  initContinuousListening();
}

function initContinuousListening() {
  var SpeechRec = window.SpeechRecognition || window.webkitSpeechRecognition;
  if (!SpeechRec) {
    console.warn('SpeechRecognition API not available in this WebKit view');
    return;
  }
  
  if (speechRecognition) return;
  
  speechRecognition = new SpeechRec();
  speechRecognition.continuous = false;
  speechRecognition.interimResults = true;
  speechRecognition.maxAlternatives = 1;
  speechRecognition.lang = 'en-US';
  
  speechRecognition.onstart = function() {
    isRecognitionRunning = true;
    console.log('[VAD] Active listening online');
  };
  
  speechRecognition.onresult = function(event) {
    if (!recognitionDesired) return;
    
    var now = Date.now();
    var nativeActive = window.ShadowNative && typeof window.ShadowNative.isSpeakingNative === 'function' && window.ShadowNative.isSpeakingNative();
    
    // ACOUSTIC ECHO SHIELD:
    // If the phone is speaking or within cooldown, discard immediately
    if (isSpeaking || isProcessingQueue || isNativeSpeaking || nativeActive || isAwaitingResponse || (now - lastAiSpeechFinishedTime < 450)) {
      activeTranscript = '';
      if (speechDebounceTimer) {
        clearTimeout(speechDebounceTimer);
        speechDebounceTimer = null;
      }
      return;
    }
    
    var lastResult = event.results[event.results.length - 1];
    if (!lastResult || !lastResult[0]) return;
    
    var cleanText = lastResult[0].transcript.trim();
    // Collapse duplicate adjacent words caused by ASR token overlaps
    cleanText = cleanText.replace(/\b([a-zA-Z]+)\s+\1\b/gi, '$1');
    if (!cleanText) return;
    
    activeTranscript = cleanText;

    if (speechDebounceTimer) {
      clearTimeout(speechDebounceTimer);
    }

    // High sensitivity speech capture:
    // Finalized utterance: commit with minimal 120ms latency
    // Interim utterance: commit after 850ms of user silence
    var debounceDelay = lastResult.isFinal ? 120 : 850;
    speechDebounceTimer = setTimeout(commitUserSpeech, debounceDelay);
  };
  
  speechRecognition.onerror = function(err) {
    isRecognitionRunning = false;
    if (err.error !== 'aborted' && recognitionDesired) {
      setTimeout(function() {
        if (recognitionDesired && !isSpeaking && !isProcessingQueue && !isNativeSpeaking && !isAwaitingResponse) {
          try { speechRecognition.start(); } catch(e) {}
        }
      }, 250);
    }
  };
  
  speechRecognition.onend = function() {
    isRecognitionRunning = false;
    
    if (speechDebounceTimer) {
      clearTimeout(speechDebounceTimer);
      speechDebounceTimer = null;
    }

    // If uncommitted speech exists when recognizer ends, commit immediately!
    if (activeTranscript && activeTranscript.trim().length > 0) {
      commitUserSpeech();
      return;
    }
    
    // Seamlessly restart for the next conversational turn
    if (recognitionDesired && !isSpeaking && !isProcessingQueue && !isNativeSpeaking && !isAwaitingResponse) {
      setTimeout(function() {
        if (recognitionDesired && !isSpeaking && !isProcessingQueue && !isNativeSpeaking && !isAwaitingResponse) {
          try { speechRecognition.start(); } catch(e) {}
        }
      }, 100);
    }
  };
  
  try {
    speechRecognition.start();
  } catch(e) {}
}

window.ShadowMouth = {
  // Speak text with synchronized lip movement & native speech
  speak: function(text, onComplete) {
    if (!text || typeof text !== 'string') return;
    speechTimeline = textToVisemes(text);
    speechStartTime = performance.now();
    isSpeaking = true;
    speechEndCallback = onComplete || null;
    
    // 1. Android Native TTS (if running inside APK)
    if (window.ShadowNative && typeof window.ShadowNative.speakNative === 'function') {
      window.ShadowNative.speakNative(text);
    } 
    // 2. Web Speech API fallback
    else if ('speechSynthesis' in window) {
      try {
        window.speechSynthesis.cancel();
        var utter = new SpeechSynthesisUtterance(text);
        utter.pitch = 0.96;
        utter.rate = 1.18;
        window.speechSynthesis.speak(utter);
      } catch (e) {}
    }
  },

  // Instant Interruption / Barge-in
  stop: stopSpeaking,
  
  // Real-time Streaming Conversation
  streamConversation: streamConversation,
  
  // Set emotional expression
  setExpression: function(name) {
    var exp = IDLE_MOUTH_EXPRESSIONS.find(function(e) { return e.name === name; });
    if (exp) {
      mouthTarget.openness = exp.openness;
      mouthTarget.widthRatio = exp.widthRatio;
      mouthTarget.cornerLeftY = exp.cornerLeftY;
      mouthTarget.cornerRightY = exp.cornerRightY;
      mouthTarget.teethVisible = exp.teethVisible;
      mouthTarget.smile = exp.smile;
    }
  },
  
  // Direct Viseme parameter override
  setViseme: function(openness, widthRatio, cornerL, cornerR, teeth, smile) {
    if (openness !== undefined) mouthTarget.openness = openness;
    if (widthRatio !== undefined) mouthTarget.widthRatio = widthRatio;
    if (cornerL !== undefined) mouthTarget.cornerLeftY = cornerL;
    if (cornerR !== undefined) mouthTarget.cornerRightY = cornerR;
    if (teeth !== undefined) mouthTarget.teethVisible = teeth;
    if (smile !== undefined) mouthTarget.smile = smile;
  },
  
  // Face spotted reaction (Visual anime smirk only - strictly silent!)
  onFaceDetected: function(faceCount) {
    mouthTarget.cornerRightY = -8;
    mouthTarget.cornerLeftY = -3;
    mouthTarget.smile = 0.24;
    mouthTarget.teethVisible = 0.45;
    mouthTarget.openness = 0.08;
    
    // Smoothly returns to neutral resting expression after 2.8s
    setTimeout(function() {
      if (!isSpeaking) {
        mouthTarget.cornerRightY = 0;
        mouthTarget.cornerLeftY = 0;
        mouthTarget.smile = 0.04;
        mouthTarget.teethVisible = 0.0;
        mouthTarget.openness = 0.0;
      }
    }, 2800);
  },
  
  // Real-time Audio reactive lip sync hook
  feedAudioRMS: function(rms, dominantFreq) {
    if (rms < 0.03) {
      mouthTarget.openness = 0.0;
      mouthTarget.teethVisible = 0.0;
      return;
    }
    mouthTarget.openness = Math.min(1.0, rms * 2.2);
    if (dominantFreq > 2200) {
      mouthTarget.widthRatio = 1.25;
      mouthTarget.teethVisible = 0.8;
      mouthTarget.smile = 0.18;
    } else if (dominantFreq < 500) {
      mouthTarget.widthRatio = 0.72;
      mouthTarget.teethVisible = 0.05;
      mouthTarget.smile = 0.02;
    } else {
      mouthTarget.widthRatio = 1.05;
      mouthTarget.teethVisible = 0.4;
      mouthTarget.smile = 0.08;
    }
  }
};

// User Touch Interruption / Silent Mic Initialization (Zero unprompted talking)
function onScreenTouch() {
  requestScreenLock();
  var nativeActive = window.ShadowNative && typeof window.ShadowNative.isSpeakingNative === 'function' && window.ShadowNative.isSpeakingNative();
  if (isSpeaking || isProcessingQueue || isNativeSpeaking || nativeActive) {
    stopSpeaking();
  } else {
    initContinuousListening();
  }
}

window.addEventListener('click', onScreenTouch);
window.addEventListener('touchstart', onScreenTouch, { passive: true });
document.addEventListener('DOMContentLoaded', function() {
  requestScreenLock();
  initContinuousListening();
});

requestAnimationFrame(render);
