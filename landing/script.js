(function () {
  "use strict";

  var APK_PATH = "tsu-sams.apk";
  var PORTAL = "https://sams.tsuniversity.edu.ng/";
  var EXAM_PORTAL = "https://sams.tsuniversity.edu.ng/exam-capture/public/";

  /* Header shadow on scroll — constant height, no layout shift */
  var header = document.querySelector(".site-header");
  var scrollTicking = false;
  function updateHeader() {
    scrollTicking = false;
    if (window.scrollY > 40) {
      header.classList.add("scrolled");
    } else {
      header.classList.remove("scrolled");
    }
  }
  window.addEventListener("scroll", function () {
    if (!scrollTicking) {
      scrollTicking = true;
      requestAnimationFrame(updateHeader);
    }
  }, { passive: true });
  updateHeader();

  /* Mobile menu */
  var burger = document.getElementById("navBurger");
  var navLinks = document.getElementById("navLinks");

  function closeNav() {
    burger.classList.remove("open");
    navLinks.classList.remove("open");
    burger.setAttribute("aria-expanded", "false");
  }

  burger.addEventListener("click", function () {
    var open = burger.classList.toggle("open");
    navLinks.classList.toggle("open", open);
    burger.setAttribute("aria-expanded", String(open));
  });

  navLinks.querySelectorAll("a").forEach(function (link) {
    link.addEventListener("click", closeNav);
  });

  document.addEventListener("keydown", function (e) {
    if (e.key === "Escape") closeNav();
  });

  /* Scroll reveal */
  var revealTargets = document.querySelectorAll(
    ".hero-copy, .hero-visual, .stats, .section-head, .card, .step, .cta-strip, .security-copy, .security-card, .download-card, .app-card, .faq details, .final-cta-inner, .footer-grid"
  );

  if ("IntersectionObserver" in window) {
    var observer = new IntersectionObserver(
      function (entries) {
        entries.forEach(function (entry) {
          if (entry.isIntersecting) {
            entry.target.classList.add("reveal", "visible");
            observer.unobserve(entry.target);
          }
        });
      },
      { threshold: 0.08, rootMargin: "0px 0px -40px 0px" }
    );
    revealTargets.forEach(function (el, i) {
      el.classList.add("reveal");
      el.style.transitionDelay = Math.min(i * 0.03, 0.18) + "s";
      observer.observe(el);
    });
  } else {
    revealTargets.forEach(function (el) {
      el.classList.add("visible");
    });
  }

  /* Download handling */
  var toast = document.getElementById("toast");
  var toastTitle = document.getElementById("toastTitle");
  var toastMsg = document.getElementById("toastMsg");
  var toastTimer = null;

  function showToast(title, message) {
    toastTitle.textContent = title;
    toastMsg.textContent = message;
    toast.classList.add("show");
    clearTimeout(toastTimer);
    toastTimer = setTimeout(function () {
      toast.classList.remove("show");
    }, 6000);
  }

  function handleDownload(e) {
    e.preventDefault();
    showToast("Preparing your download…", "If the file doesn't start, tap below to open the SAMS portal.");
    fetch(APK_PATH, { method: "HEAD" })
      .then(function (res) {
        if (res.ok) {
          var link = document.createElement("a");
          link.href = APK_PATH;
          link.download = "tsu-sams.apk";
          document.body.appendChild(link);
          link.click();
          document.body.removeChild(link);
          showToast("Download starting…", "Install the APK on your Android device to begin enrollment.");
        } else {
          throw new Error("no-apk");
        }
      })
      .catch(function () {
        showToast("Official app", "The APK is available from the ICT Directorate. Open the SAMS portal to enroll and verify your biometrics.");
      });
  }

  var downloadBtn = document.getElementById("downloadBtn");
  if (downloadBtn) {
    downloadBtn.addEventListener("click", handleDownload);
  }

  /* Smooth anchor scroll with header offset */
  document.querySelectorAll('a[href^="#"]').forEach(function (link) {
    link.addEventListener("click", function (e) {
      var targetId = link.getAttribute("href");
      if (targetId === "#" || targetId === "#download") return;
      var target = document.querySelector(targetId);
      if (!target) return;
      e.preventDefault();
      var top = target.getBoundingClientRect().top + window.scrollY - 88;
      window.scrollTo({ top: top, behavior: "smooth" });
    });
  });
})();