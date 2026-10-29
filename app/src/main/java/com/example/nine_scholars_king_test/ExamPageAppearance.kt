package com.example.nine_scholars_king_test

internal object ExamPageAppearance {
    /** A persistent stylesheet also covers a title rendered later by the page's SPA. */
    val hideTitleScript = """
        (function () {
          if (window.top !== window) return;
          var styleId = 'jxw-host-exam-appearance';
          function install() {
            var root = document.head || document.documentElement;
            if (!root) return false;
            if (!document.getElementById(styleId)) {
              var style = document.createElement('style');
              style.id = styleId;
              style.textContent =
                '.exam-page .exam-title-bar { display: none !important; }' +
                '.exam-page .sidebar-wrapper, .exam-page .header-wrapper { top: 24px !important; }' +
                '@media (min-width: 768px) { .exam-page .content-wrapper { padding-top: 136px !important; } }' +
                '@media (max-width: 767px) { .exam-page .content-wrapper { padding-top: 16px !important; } }';
              root.appendChild(style);
            }
            return true;
          }
          if (!install()) {
            var observer = new MutationObserver(function () {
              if (install()) observer.disconnect();
            });
            observer.observe(document, { childList: true, subtree: true });
          }
        })();
    """.trimIndent()
}
