// ============================================================================
// NUSA DYNAMIC NOTCH FOR REDMI NOTE 13 PRO 5G - CORE APPLICATION LOGIC
// ============================================================================

(function () {
  'use strict';

  // State Management
  const state = {
    currentState: 'pet', // pet, idle, music, lyrics, pomo, water, shelf, todo, notif, charge
    previousState: 'pet',
    isExpanded: false,
    isFullscreen: false,
    // Music Player State
    music: {
      isPlaying: true,
      currentTime: 179, // 2:59
      duration: 256,    // 4:16
      title: 'Paper Sky',
      artist: 'Lumen Coast - Summer Flows',
      currentLyricIndex: 2
    },
    // Pomodoro State
    pomo: {
      isRunning: false,
      timeLeft: 25 * 60, // 25 minutes in seconds
      totalTime: 25 * 60,
      round: 2,
      maxRounds: 4,
      intervalId: null
    },
    // Water State
    water: {
      current: 1.5,
      goal: 2.0,
      chart: [200, 350, 400, 250, 300]
    },
    // Shelf Notes
    shelfItems: [
      { id: 1, title: 'Meeting Notes', snippet: 'Review Redmi 13 Pro 5G UI' },
      { id: 2, title: 'GitHub Repo', snippet: 'github.com/morluto/rea' }
    ],
    // Todo items
    todos: [
      { id: 1, text: 'Calibrate notch for Redmi punch hole', done: true },
      { id: 2, text: 'Test synced lyrics & audio controls', done: false },
      { id: 3, text: 'Verify 120Hz smooth animations', done: true }
    ]
  };

  const lyrics = [
    { time: 10, text: 'we fold the summer light' },
    { time: 35, text: 'into paper birds tonight' },
    { time: 65, text: 'and send them through the sky' },
    { time: 95, text: 'to find you by the sea' },
    { time: 125, text: 'every wing a little song' },
    { time: 155, text: 'that carries us along' },
    { time: 190, text: 'where oceans meet the shore' },
    { time: 220, text: 'and shadows fade once more' }
  ];

  // DOM Elements
  const notchEl = document.getElementById('nusaNotch');
  const ambientLight = document.getElementById('ambientLight');
  const stateButtons = document.querySelectorAll('.state-btn');
  const notchDots = document.querySelectorAll('.notch-dot');

  // Clock elements
  const idleTimeEl = document.getElementById('idleTime');
  const statusTimeEl = document.getElementById('statusTime');
  const bigClockEl = document.getElementById('bigClock');
  const dateEl = document.getElementById('widgetDate');

  // Music elements
  const playBtn = document.getElementById('playPauseBtn');
  const playIcon = document.getElementById('playIcon');
  const prevBtn = document.getElementById('prevTrackBtn');
  const nextBtn = document.getElementById('nextTrackBtn');
  const trackTimeEl = document.getElementById('trackTime');
  const trackRemainingEl = document.getElementById('trackRemaining');
  const progressFill = document.getElementById('musicProgressFill');
  const progressTrack = document.getElementById('musicProgressTrack');
  const lyricsPillBtn = document.getElementById('lyricsPillBtn');
  const lyricsBackBtn = document.getElementById('lyricsBackBtn');
  const lyricsBox = document.getElementById('lyricsBox');

  // Pomodoro elements
  const pomoTimeEl = document.getElementById('pomoTime');
  const pomoToggleBtn = document.getElementById('pomoToggleBtn');
  const pomoResetBtn = document.getElementById('pomoResetBtn');
  const pomoCircleBar = document.getElementById('pomoCircleBar');
  const pomoRoundLabel = document.getElementById('pomoRoundLabel');

  // Water elements
  const waterCurrentEl = document.getElementById('waterCurrent');
  const waterGoalEl = document.getElementById('waterGoal');
  const waterPctEl = document.getElementById('waterPct');
  const drinkBtn = document.getElementById('drinkWaterBtn');
  const chartBars = document.querySelectorAll('.chart-bar');

  // Shelf elements
  const shelfItemsContainer = document.getElementById('shelfItemsContainer');
  const shelfInput = document.getElementById('shelfInput');
  const shelfAddBtn = document.getElementById('shelfAddBtn');
  const shelfClearBtn = document.getElementById('shelfClearBtn');

  // Todo elements
  const todoListContainer = document.getElementById('todoListContainer');

  // Switcher & Mode buttons
  const toggleModeBtn = document.getElementById('toggleModeBtn');

  // --------------------------------------------------------------------------
  // Initialization
  // --------------------------------------------------------------------------
  function init() {
    updateClock();
    setInterval(updateClock, 1000);

    initMusicPlayer();
    initPomodoro();
    initWater();
    renderShelf();
    renderTodos();
    setupEventListeners();
    setupPwa();

    // Default state: Pet mascot
    setState('pet');
  }

  // --------------------------------------------------------------------------
  // Notch State Controller
  // --------------------------------------------------------------------------
  function setState(newState) {
    state.previousState = state.currentState;
    state.currentState = newState;
    notchEl.setAttribute('data-state', newState);

    const isExpanded = ['music', 'lyrics', 'pomo', 'water', 'shelf', 'todo'].includes(newState);
    state.isExpanded = isExpanded;

    // Update buttons in control panel
    stateButtons.forEach(btn => {
      if (btn.getAttribute('data-s') === newState) {
        btn.classList.add('active');
      } else {
        btn.classList.remove('active');
      }
    });

    // Update bottom dot navigation
    const pages = ['music', 'pomo', 'water', 'shelf', 'todo'];
    const pageIndex = pages.indexOf(newState);
    notchDots.forEach((dot, idx) => {
      if (idx === pageIndex) {
        dot.classList.add('active');
      } else {
        dot.classList.remove('active');
      }
    });

    // Ambient glow effect
    if (newState === 'pet' || newState === 'lyrics' || newState === 'music') {
      ambientLight.style.opacity = '0.35';
    } else {
      ambientLight.style.opacity = '0.15';
    }
  }

  // --------------------------------------------------------------------------
  // Clock & Time
  // --------------------------------------------------------------------------
  function updateClock() {
    const now = new Date();
    const hours = String(now.getHours()).padStart(2, '0');
    const minutes = String(now.getMinutes()).padStart(2, '0');
    const timeStr = `${hours}:${minutes}`;

    if (idleTimeEl) idleTimeEl.textContent = timeStr;
    if (statusTimeEl) statusTimeEl.textContent = timeStr;
    if (bigClockEl) bigClockEl.textContent = timeStr;

    if (dateEl) {
      const options = { weekday: 'short', month: 'short', day: 'numeric' };
      dateEl.textContent = now.toLocaleDateString('vi-VN', options);
    }
  }

  // --------------------------------------------------------------------------
  // Music Player & Synced Lyrics
  // --------------------------------------------------------------------------
  function initMusicPlayer() {
    renderLyrics();
    updateMusicDisplay();

    // Simulated music playback progress ticker
    setInterval(() => {
      if (state.music.isPlaying) {
        state.music.currentTime++;
        if (state.music.currentTime >= state.music.duration) {
          state.music.currentTime = 0;
        }
        updateMusicDisplay();
        syncLyrics();
      }
    }, 1000);
  }

  function formatTime(secs) {
    const m = Math.floor(secs / 60);
    const s = Math.floor(secs % 60);
    return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`;
  }

  function updateMusicDisplay() {
    if (trackTimeEl) trackTimeEl.textContent = formatTime(state.music.currentTime);
    const remaining = state.music.duration - state.music.currentTime;
    if (trackRemainingEl) trackRemainingEl.textContent = `-${formatTime(remaining)}`;

    const pct = (state.music.currentTime / state.music.duration) * 100;
    if (progressFill) progressFill.style.width = `${pct}%`;
  }

  function renderLyrics() {
    if (!lyricsBox) return;
    lyricsBox.innerHTML = '';
    lyrics.forEach((item, index) => {
      const lineEl = document.createElement('div');
      lineEl.className = 'lyrics-line';
      if (index === state.music.currentLyricIndex) lineEl.classList.add('active');
      lineEl.textContent = item.text;
      lineEl.addEventListener('click', () => {
        state.music.currentTime = item.time;
        updateMusicDisplay();
        syncLyrics();
      });
      lyricsBox.appendChild(lineEl);
    });
  }

  function syncLyrics() {
    let activeIdx = 0;
    for (let i = 0; i < lyrics.length; i++) {
      if (state.music.currentTime >= lyrics[i].time) {
        activeIdx = i;
      }
    }
    if (activeIdx !== state.music.currentLyricIndex) {
      state.music.currentLyricIndex = activeIdx;
      const lines = lyricsBox.querySelectorAll('.lyrics-line');
      lines.forEach((l, idx) => {
        if (idx === activeIdx) {
          l.classList.add('active');
          l.scrollIntoView({ behavior: 'smooth', block: 'center' });
        } else {
          l.classList.remove('active');
        }
      });
    }
  }

  function togglePlayPause() {
    state.music.isPlaying = !state.music.isPlaying;
    if (playIcon) {
      playIcon.innerHTML = state.music.isPlaying
        ? '<path d="M7 5h4v14H7zM13 5h4v14h-4z"/>'
        : '<path d="M8 5v14l11-7z"/>';
    }
  }

  // --------------------------------------------------------------------------
  // Pomodoro Timer
  // --------------------------------------------------------------------------
  function initPomodoro() {
    updatePomoDisplay();
  }

  function updatePomoDisplay() {
    const mins = Math.floor(state.pomo.timeLeft / 60);
    const secs = state.pomo.timeLeft % 60;
    if (pomoTimeEl) {
      pomoTimeEl.textContent = `${String(mins).padStart(2, '0')}:${String(secs).padStart(2, '0')}`;
    }

    if (pomoCircleBar) {
      const circumference = 2 * Math.PI * 36; // r=36 -> ~226
      const fraction = 1 - (state.pomo.timeLeft / state.pomo.totalTime);
      const offset = circumference * (1 - fraction);
      pomoCircleBar.style.strokeDashoffset = offset;
    }

    if (pomoRoundLabel) {
      pomoRoundLabel.textContent = `${state.pomo.round}/${state.pomo.maxRounds}`;
    }
  }

  function togglePomo() {
    if (state.pomo.isRunning) {
      clearInterval(state.pomo.intervalId);
      state.pomo.isRunning = false;
      if (pomoToggleBtn) pomoToggleBtn.textContent = 'Start';
      if (pomoToggleBtn) pomoToggleBtn.classList.remove('active');
    } else {
      state.pomo.isRunning = true;
      if (pomoToggleBtn) pomoToggleBtn.textContent = 'Pause';
      if (pomoToggleBtn) pomoToggleBtn.classList.add('active');
      state.pomo.intervalId = setInterval(() => {
        if (state.pomo.timeLeft > 0) {
          state.pomo.timeLeft--;
          updatePomoDisplay();
        } else {
          clearInterval(state.pomo.intervalId);
          state.pomo.isRunning = false;
          alert('Pomodoro round complete! Time for a short break.');
          state.pomo.round = (state.pomo.round % state.pomo.maxRounds) + 1;
          state.pomo.timeLeft = state.pomo.totalTime;
          updatePomoDisplay();
        }
      }, 1000);
    }
  }

  function resetPomo() {
    clearInterval(state.pomo.intervalId);
    state.pomo.isRunning = false;
    state.pomo.timeLeft = state.pomo.totalTime;
    if (pomoToggleBtn) pomoToggleBtn.textContent = 'Start';
    if (pomoToggleBtn) pomoToggleBtn.classList.remove('active');
    updatePomoDisplay();
  }

  // --------------------------------------------------------------------------
  // Water Tracker
  // --------------------------------------------------------------------------
  function initWater() {
    updateWaterDisplay();
  }

  function updateWaterDisplay() {
    if (waterCurrentEl) waterCurrentEl.textContent = state.water.current.toFixed(1);
    if (waterGoalEl) waterGoalEl.textContent = `/ ${state.water.goal.toFixed(1)} L`;
    const pct = Math.min(100, Math.round((state.water.current / state.water.goal) * 100));
    if (waterPctEl) waterPctEl.textContent = `${pct}%`;

    // Update chart
    chartBars.forEach((bar, idx) => {
      const val = state.water.chart[idx] || 150;
      const heightPct = Math.min(100, Math.round((val / 400) * 100));
      bar.style.height = `${heightPct}%`;
    });
  }

  function drinkWater() {
    state.water.current += 0.25;
    state.water.chart[state.water.chart.length - 1] += 50;
    updateWaterDisplay();
  }

  // --------------------------------------------------------------------------
  // Shelf & Clipboard Scratchpad
  // --------------------------------------------------------------------------
  function renderShelf() {
    if (!shelfItemsContainer) return;
    shelfItemsContainer.innerHTML = '';
    state.shelfItems.forEach(item => {
      const card = document.createElement('div');
      card.className = 'shelf-card';
      card.innerHTML = `
        <div class="shelf-card-title">${item.title}</div>
        <div class="shelf-card-snippet">${item.snippet}</div>
      `;
      card.addEventListener('click', () => {
        navigator.clipboard.writeText(item.snippet).then(() => {
          showNotifBanner('Copied to clipboard!', item.snippet);
        }).catch(() => {
          showNotifBanner('Shelf Item', item.snippet);
        });
      });
      shelfItemsContainer.appendChild(card);
    });
  }

  function addShelfItem() {
    const text = shelfInput.value.trim();
    if (!text) return;
    state.shelfItems.unshift({
      id: Date.now(),
      title: 'Quick Note',
      snippet: text
    });
    shelfInput.value = '';
    renderShelf();
  }

  // --------------------------------------------------------------------------
  // Todo List
  // --------------------------------------------------------------------------
  function renderTodos() {
    if (!todoListContainer) return;
    todoListContainer.innerHTML = '';
    state.todos.forEach(todo => {
      const item = document.createElement('div');
      item.className = `todo-item ${todo.done ? 'done' : ''}`;
      item.innerHTML = `
        <div class="todo-check">${todo.done ? '✓' : ''}</div>
        <span>${todo.text}</span>
      `;
      item.addEventListener('click', () => {
        todo.done = !todo.done;
        renderTodos();
      });
      todoListContainer.appendChild(item);
    });
  }

  // --------------------------------------------------------------------------
  // Notification Banner Helper
  // --------------------------------------------------------------------------
  function showNotifBanner(title, desc) {
    const notifTitle = document.getElementById('notifTitle');
    const notifDesc = document.getElementById('notifDesc');
    if (notifTitle) notifTitle.textContent = title;
    if (notifDesc) notifDesc.textContent = desc;

    setState('notif');
    setTimeout(() => {
      setState('pet');
    }, 3200);
  }

  // --------------------------------------------------------------------------
  // Event Listeners
  // --------------------------------------------------------------------------
  function setupEventListeners() {
    // Notch click: toggle between idle/pet and expanded view
    notchEl.addEventListener('click', (e) => {
      // Don't toggle if clicking on an interactive button inside the notch
      if (e.target.closest('button') || e.target.closest('input') || e.target.closest('.lyrics-line') || e.target.closest('.todo-item') || e.target.closest('.shelf-card') || e.target.closest('#musicProgressTrack')) {
        return;
      }
      if (state.isExpanded) {
        setState('pet');
      } else {
        setState('music');
      }
    });

    // Control buttons in bottom control panel
    stateButtons.forEach(btn => {
      btn.addEventListener('click', () => {
        const s = btn.getAttribute('data-s');
        setState(s);
      });
    });

    // Music controls
    if (playBtn) playBtn.addEventListener('click', togglePlayPause);
    if (prevBtn) prevBtn.addEventListener('click', () => {
      state.music.currentTime = 0;
      updateMusicDisplay();
    });
    if (nextBtn) nextBtn.addEventListener('click', () => {
      state.music.currentTime = 0;
      updateMusicDisplay();
    });

    if (progressTrack) {
      progressTrack.addEventListener('click', (e) => {
        const rect = progressTrack.getBoundingClientRect();
        const clickX = e.clientX - rect.left;
        const pct = Math.max(0, Math.min(1, clickX / rect.width));
        state.music.currentTime = Math.floor(pct * state.music.duration);
        updateMusicDisplay();
        syncLyrics();
      });
    }

    if (lyricsPillBtn) lyricsPillBtn.addEventListener('click', () => setState('lyrics'));
    if (lyricsBackBtn) lyricsBackBtn.addEventListener('click', () => setState('music'));

    // Pomodoro controls
    if (pomoToggleBtn) pomoToggleBtn.addEventListener('click', togglePomo);
    if (pomoResetBtn) pomoResetBtn.addEventListener('click', resetPomo);

    // Water controls
    if (drinkBtn) drinkBtn.addEventListener('click', drinkWater);

    // Shelf controls
    if (shelfAddBtn) shelfAddBtn.addEventListener('click', addShelfItem);
    if (shelfInput) shelfInput.addEventListener('keydown', (e) => {
      if (e.key === 'Enter') addShelfItem();
    });
    if (shelfClearBtn) shelfClearBtn.addEventListener('click', () => {
      state.shelfItems = [];
      renderShelf();
    });

    // Toggle Standalone vs Phone Simulator Mode
    if (toggleModeBtn) {
      toggleModeBtn.addEventListener('click', () => {
        state.isFullscreen = !state.isFullscreen;
        document.body.classList.toggle('fullscreen-mode', state.isFullscreen);
        toggleModeBtn.textContent = state.isFullscreen ? 'Exit Fullscreen' : 'Open On Phone';
      });
    }

    // Touch Swipe gestures on the notch to flip through pages
    let touchStartX = 0;
    let touchStartY = 0;

    notchEl.addEventListener('touchstart', (e) => {
      touchStartX = e.changedTouches[0].screenX;
      touchStartY = e.changedTouches[0].screenY;
    }, { passive: true });

    notchEl.addEventListener('touchend', (e) => {
      const touchEndX = e.changedTouches[0].screenX;
      const touchEndY = e.changedTouches[0].screenY;
      handleSwipe(touchStartX, touchStartY, touchEndX, touchEndY);
    }, { passive: true });
  }

  function handleSwipe(startX, startY, endX, endY) {
    const deltaX = endX - startX;
    const deltaY = endY - startY;
    const pages = ['music', 'pomo', 'water', 'shelf', 'todo'];

    // Vertical swipe down from closed notch -> expand
    if (!state.isExpanded && deltaY > 30) {
      setState('music');
      return;
    }
    // Vertical swipe up from expanded notch -> collapse
    if (state.isExpanded && deltaY < -40) {
      setState('pet');
      return;
    }

    // Horizontal swipe between pages
    if (state.isExpanded && Math.abs(deltaX) > 40) {
      const currentIndex = pages.indexOf(state.currentState);
      if (currentIndex !== -1) {
        if (deltaX < 0 && currentIndex < pages.length - 1) {
          // Swipe Left -> Next page
          setState(pages[currentIndex + 1]);
        } else if (deltaX > 0 && currentIndex > 0) {
          // Swipe Right -> Prev page
          setState(pages[currentIndex - 1]);
        }
      }
    }
  }

  // --------------------------------------------------------------------------
  // Service Worker Registration for PWA Installation on Redmi Note 13 Pro 5G
  // --------------------------------------------------------------------------
  function setupPwa() {
    if ('serviceWorker' in navigator) {
      window.addEventListener('load', () => {
        navigator.serviceWorker.register('./sw.js').then(
          (reg) => console.log('Nusa PWA Service Worker registered:', reg.scope),
          (err) => console.log('Service Worker registration failed:', err)
        );
      });
    }
  }

  // Run on DOM ready
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();
