(function () {
  const STORAGE_KEY = "msc_lang";

  function detectLang() {
    const saved = localStorage.getItem(STORAGE_KEY);
    if (saved === "en" || saved === "es") return saved;
    const nav = (navigator.language || navigator.userLanguage || "en").toLowerCase();
    if (nav.startsWith("es")) return "es";
    return "en";
  }

  function setLang(lang) {
    document.documentElement.setAttribute("data-lang", lang);
    localStorage.setItem(STORAGE_KEY, lang);
    document.querySelectorAll(".lang-btn").forEach((b) => {
      b.classList.toggle("active", b.dataset.lang === lang);
    });
  }

  // Guide tabs
  function initGuide() {
    const buttons = document.querySelectorAll(".guide-nav button");
    const panels = document.querySelectorAll(".guide-panel");
    buttons.forEach((btn) => {
      btn.addEventListener("click", () => {
        const target = btn.dataset.target;
        buttons.forEach((b) => b.classList.toggle("active", b === btn));
        panels.forEach((p) => {
          p.style.display = p.id === target ? "block" : "none";
        });
      });
    });
    // show first
    if (panels.length) {
      panels.forEach((p, i) => (p.style.display = i === 0 ? "block" : "none"));
      buttons[0]?.classList.add("active");
    }
  }

  document.addEventListener("DOMContentLoaded", () => {
    setLang(detectLang());
    document.querySelectorAll(".lang-btn").forEach((b) => {
      b.addEventListener("click", () => setLang(b.dataset.lang));
    });
    initGuide();
  });
})();