(function () {
  var reduce = matchMedia("(prefers-reduced-motion: reduce)").matches;

  function goTo(hash, push) {
    var id = hash.replace("#", "") || "top";
    var target = document.getElementById(id);
    if (!target) return;
    target.scrollIntoView({ behavior: reduce ? "auto" : "smooth", block: "start" });
    if (push && history.replaceState) history.replaceState(null, "", "#" + id);
  }
  document.querySelectorAll('a[href^="#"]').forEach(function (a) {
    a.addEventListener("click", function (e) {
      e.preventDefault();
      goTo(a.getAttribute("href"), true);
    });
  });
  addEventListener("hashchange", function () { goTo(location.hash, false); });
  if (location.hash) setTimeout(function () { goTo(location.hash, false); }, 60);

  var els = document.querySelectorAll(".reveal");
  if ("IntersectionObserver" in window) {
    var io = new IntersectionObserver(function (entries) {
      entries.forEach(function (e) { if (e.isIntersecting) { e.target.classList.add("in"); io.unobserve(e.target); } });
    }, { threshold: 0.12 });
    els.forEach(function (el) { io.observe(el); });
  } else {
    els.forEach(function (el) { el.classList.add("in"); });
  }

  var cyc = document.querySelectorAll("[data-cycle]");
  var idx = 0;
  function step() {
    idx = (idx + 1) % 4;
    cyc.forEach(function (box) {
      var list = JSON.parse(box.dataset.cycle);
      var item = list[idx % list.length];
      box.classList.add("out");
      setTimeout(function () {
        box.querySelector(".pv-verb").textContent = item[0] + " " + item[1];
        box.querySelector(".pv-sub").textContent = item[2];
        box.classList.remove("out");
      }, 350);
    });
  }
  if (!reduce && cyc.length) setInterval(step, 3200);
})();
