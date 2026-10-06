/* =======================================================================
   PART 1/6 — VITC Success Stories (public page)
   Loads video success stories per category (student/teacher/parent) from
   the backend REST API and drives the video modal. Self-contained and only
   ever runs when #storiesGrid exists on the page, so it never touches any
   other page. Depends on assets/js/api.js being loaded first.
   ======================================================================= */
(function (window, document) {
  "use strict";

  var V = window.VITC;
  if (!V) return;

  function el(id) { return document.getElementById(id); }

  var CATEGORY_LABEL = { all: "all", student: "student", teacher: "teacher", parent: "parent" };
  var publishedStories = [];
  var selectedCategory = "all";

  function storyCategory(story) {
    return String(story && story.category || "").trim().toLowerCase();
  }

  function storyCard(s) {
    /* Only the thumbnail image loads with the card; the video file itself is never
       fetched until the user clicks to play it (see openModal below), and the
       thumbnail uses native loading="lazy" (same pattern as gallery.js/site.js)
       so off-screen cards don't cost a request until scrolled into view. */
    var thumb = V.mediaUrl(s.thumbnailUrl) || "assets/img/logo.jpeg";
    var video = V.mediaUrl(s.videoUrl);
    return '<div class="card story-card" data-video="' + V.esc(video) + '" data-name="' + V.esc(s.name) +
      '" tabindex="0" role="button" aria-label="Play video story: ' + V.esc(s.name) + '">' +
      '<div class="story-thumb">' +
      '<img src="' + V.esc(thumb) + '" alt="" loading="lazy" width="400" height="250" onerror="this.onerror=null;this.src=\'assets/img/logo.jpeg\'">' +
      '<span class="story-play" aria-hidden="true">&#9654;</span></div>' +
      '<div class="story-body">' +
      (s.category ? '<span class="story-tag">' + V.esc(s.category) + '</span><br>' : '') +
      '<h3>' + V.esc(s.name) + '</h3>' +
      (s.designation ? '<p class="story-role">' + V.esc(s.designation) + '</p>' : '') +
      (s.courseProgram ? '<p class="story-course">' + V.esc(s.courseProgram) + '</p>' : '') +
      (s.description ? '<p class="story-desc">' + V.esc(s.description) + '</p>' : '') +
      '</div></div>';
  }

  function emptyMessage(cat) {
    return "No " + (CATEGORY_LABEL[cat] || cat) + " success stories available yet.";
  }

  function normalizeCategory(category) {
    return String(category || "all").trim().toLowerCase();
  }

  function bindCards(host) {
    host.querySelectorAll("[data-video]").forEach(function (card) {
      function open() { openModal(card.dataset.video, card.dataset.name); }
      card.addEventListener("click", open);
      card.addEventListener("keydown", function (e) {
        if (e.key === "Enter" || e.key === " ") { e.preventDefault(); open(); }
      });
    });
  }

  function renderStories(category) {
    var host = el("storiesGrid");
    if (!host) return;
    var cat = normalizeCategory(category);
    var items = cat === "all" ? publishedStories : publishedStories.filter(function (story) {
      return storyCategory(story) === cat;
    });
    if (!items.length) {
      V.setState(host, "empty", emptyMessage(cat));
      return;
    }
    host.innerHTML = items.map(storyCard).join("");
    bindCards(host);
  }

  function loadStories() {
    var host = el("storiesGrid");
    if (!host) return;
    V.load(host, V.api.successStories, function (items) {
      publishedStories = Array.isArray(items) ? items : [];
      renderStories(selectedCategory);
    }, emptyMessage("all"));
  }

  function bindTabs() {
    var buttons = document.querySelectorAll("[data-story-tab]");
    if (!buttons.length) return;
    buttons.forEach(function (b) {
      b.addEventListener("click", function () {
        buttons.forEach(function (x) { x.classList.remove("active"); x.setAttribute("aria-selected", "false"); });
        b.classList.add("active");
        b.setAttribute("aria-selected", "true");
        selectedCategory = normalizeCategory(b.dataset.storyTab);
        renderStories(selectedCategory);
      });
    });
  }

  /* ------------------------------ video modal ------------------------------ */
  function openModal(src, name) {
    var modal = el("videoModal");
    if (!modal || !src) return;
    var video = modal.querySelector("video");
    /* Playback failure (corrupt file, network drop, unsupported codec, etc.) shows a
       friendly toast instead of a silent black box or a raw browser/console error. */
    video.onerror = function () {
      V.toast("This video could not be played. Please try again later.", "err");
    };
    video.src = src;
    video.currentTime = 0;
    var title = modal.querySelector(".video-modal-title");
    if (title) title.textContent = name || "";
    modal.classList.add("open");
    document.body.classList.add("modal-open");
    /* Play/pause/volume/fullscreen come from the native <video controls>
       element. This play() only runs because the user just clicked a video
       card — it is not a page-load autoplay. */
    video.play().catch(function () { /* autoplay policies vary by browser; controls remain available */ });
  }

  function closeModal() {
    var modal = el("videoModal");
    if (!modal) return;
    var video = modal.querySelector("video");
    video.pause();
    video.onerror = null; /* clearing the src below would otherwise re-trigger the error toast */
    video.removeAttribute("src");
    video.load();
    modal.classList.remove("open");
    document.body.classList.remove("modal-open");
  }

  function bindModal() {
    var modal = el("videoModal");
    if (!modal) return;
    modal.addEventListener("click", function (e) {
      if (e.target === modal || e.target.classList.contains("video-modal-close")) closeModal();
    });
    document.addEventListener("keydown", function (e) {
      if (e.key === "Escape" && modal.classList.contains("open")) closeModal();
    });
  }

  document.addEventListener("DOMContentLoaded", function () {
    if (!el("storiesGrid")) return;
    bindTabs();
    bindModal();
    loadStories();
  });
})(window, document);
