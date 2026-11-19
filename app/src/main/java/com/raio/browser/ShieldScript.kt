package com.raio.browser

/** Cosmetic filtering only. Never alters content videos, clicks ads, or removes consent dialogs. */
object ShieldScript {
    val source = """
        (function() {
          if (window.__raioShield) return;
          window.__raioShield = true;
          var selectors = [
            'ins.adsbygoogle', '[id^="google_ads_iframe"]', '[id^="div-gpt-ad"]',
            '[data-ad-client]', '[data-ad-slot]', '.ad-banner', '.ad-container',
            '.advertisement', '.advertising', '.taboola-recommendations',
            '[id^="taboola-"]', '.OUTBRAIN', '[data-outbrain]',
            'ytd-ad-slot-renderer', 'ytd-display-ad-renderer',
            'ytd-promoted-sparkles-web-renderer', 'ytd-promoted-video-renderer',
            'ytm-promoted-sparkles-web-renderer', 'ytm-companion-ad-renderer',
            'ytm-ad-slot-renderer', 'ytd-action-companion-ad-renderer',
            '.ytp-ad-overlay-container', '#player-ads', '#masthead-ad'
          ];
          var style = document.createElement('style');
          style.id = 'raio-shield-style';
          style.textContent = selectors.join(',') + '{display:none!important; min-height:0!important; height:0!important}';
          (document.head || document.documentElement).appendChild(style);
          var youtube = /(^|\.)youtube\.com$/.test(location.hostname);
          if (!youtube) return;
          var queued = false;
          function skipAvailableAd() {
            queued = false;
            // Click only YouTube's explicit skip control, never the advertisement itself.
            var player = document.querySelector('.ad-showing');
            if (!player) return;
            var skip = player.querySelector('.ytp-ad-skip-button, .ytp-ad-skip-button-modern, .ytp-skip-ad-button');
            if (skip && skip.getClientRects().length && !skip.disabled) skip.click();
          }
          var observer = new MutationObserver(function() {
            if (!queued) { queued = true; setTimeout(skipAvailableAd, 250); }
          });
          observer.observe(document.documentElement, {childList:true, subtree:true, attributes:true, attributeFilter:['class']});
          window.addEventListener('pagehide', function(e) { if (!e.persisted) observer.disconnect(); });
          skipAvailableAd();
        })();
    """.trimIndent()
}
