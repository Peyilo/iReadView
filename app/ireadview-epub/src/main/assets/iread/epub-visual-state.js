/* Host-owned invalidation signal. ES5 syntax for API21 WebViews. */
(function (config) {
    'use strict';
    if (!config.visualSignal) return;
    var rulesDirty = true, movingStyles = true, port;
    function hasMovingStyles() {
        if (!rulesDirty) return movingStyles;
        rulesDirty = false;
        movingStyles = false;
        try {
            for (var i = 0; i < document.styleSheets.length; i++) {
                var rules = document.styleSheets[i].cssRules;
                for (var j = 0; j < rules.length; j++) {
                    if (rules[j].type === CSSRule.IMPORT_RULE ||
                        (rules[j].type !== CSSRule.FONT_FACE_RULE && changingPaint.test(rules[j].cssText))) movingStyles = true;
                }
            }
        } catch (e) { movingStyles = true; }
        return movingStyles;
    }
    var changingPaint = /animation|transition|url\(/i;
    function staticViewport() {
        if (document.fonts && document.fonts.status !== 'loaded') return false;
        if (hasMovingStyles()) return false;
        var inline = document.querySelectorAll('[style]');
        for (var i = 0; i < inline.length; i++) {
            if (changingPaint.test(inline[i].getAttribute('style'))) return false;
        }
        var media = document.querySelectorAll('img,svg,canvas,video,audio,iframe,object,embed,input,textarea,select,button,progress,meter,marquee');
        for (var j = 0; j < media.length; j++) {
            var r = media[j].getBoundingClientRect();
            if (r.right > 0 && r.left < config.width && r.bottom > 0 && r.top < config.height) return false;
        }
        return true;
    }
    function changed() {
        if (port) port.postMessage(staticViewport() ? '1' : '0');
    }
    if (typeof MutationObserver !== 'undefined') {
        new MutationObserver(function () { rulesDirty = true; changed(); }).observe(document.documentElement,
            {subtree: true, childList: true, attributes: true, characterData: true});
        var events = ['scroll', 'load', 'input', 'change', 'focusin', 'focusout', 'mousedown', 'mouseup',
            'touchstart', 'touchend', 'selectionchange', 'animationstart', 'animationend', 'transitionend'];
        for (var i = 0; i < events.length; i++) document.addEventListener(events[i], changed, true);
        if (document.fonts && document.fonts.addEventListener) {
            document.fonts.addEventListener('loading', changed);
            document.fonts.addEventListener('loadingdone', changed);
        }
        window.addEventListener('message', function connect(event) {
            if (event.data !== config.visualSignal || event.ports.length !== 1) return;
            window.removeEventListener('message', connect);
            port = event.ports[0];
            changed();
        });
    }
})
