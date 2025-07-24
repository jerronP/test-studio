package com.recorder.util;

public class RecorderScriptUtil {

    public static String getRecorderScript() {
        return """
        (function() {
          if (window.__recorderInjected) return;
          window.__recorderInjected = true;

          function getBestLocator(el) {
            if (el.id && document.querySelectorAll('#' + el.id).length === 1) {
              return { findBy: 'id', value: el.id };
            }
            if (el.name && document.querySelectorAll('[name="' + el.name + '"]').length === 1) {
              return { findBy: 'name', value: el.name };
            }
            try {
              const css = getCssPath(el);
              if (document.querySelectorAll(css).length === 1) {
                return { findBy: 'cssSelector', value: css };
              }
            } catch (e) {}
            return { findBy: 'xpath', value: getXPath(el) };
          }

          function getCssPath(el) {
            const path = [];
            while (el.nodeType === Node.ELEMENT_NODE) {
              let selector = el.nodeName.toLowerCase();
              if (el.id) {
                selector += '#' + el.id;
                path.unshift(selector);
                break;
              } else {
                let sib = el, nth = 1;
                while ((sib = sib.previousElementSibling)) {
                  if (sib.nodeName.toLowerCase() === selector) nth++;
                }
                selector += ":nth-of-type(" + nth + ")";
              }
              path.unshift(selector);
              el = el.parentNode;
            }
            return path.join(" > ");
          }

          function getXPath(el) {
            if (el.id) return '//*[@id="' + el.id + '"]';
            if (el === document.body) return '/html/body';
            let ix = 0;
            const siblings = el.parentNode ? el.parentNode.childNodes : [];
            for (let i = 0; i < siblings.length; i++) {
              const sibling = siblings[i];
              if (sibling === el) return getXPath(el.parentNode) + '/' + el.tagName.toLowerCase() + '[' + (ix + 1) + ']';
              if (sibling.nodeType === 1 && sibling.tagName === el.tagName) ix++;
            }
          }

          function getAllAttributes(el) {
            const attrs = {};
            if (!el.attributes) return attrs;
            for (let attr of el.attributes) {
              const name = attr.name;
              const value = attr.value;
              if (
                name.startsWith('_ngcontent') ||
                name.startsWith('_nghost') ||
                name === 'loading' ||
                name === 'style' ||
                name === 'onclick'
              ) continue;
              attrs[name] = value;
            }
            return attrs;
          }

          function send(action, el, value = '') {
            const locator = getBestLocator(el);
            const payload = {
              action: action,
              selector: locator.value,
              value: value,
              tag: el.tagName,
              text: el.innerText || '',
              name: el.getAttribute("name") || el.getAttribute("aria-label") || el.getAttribute("placeholder") || '',
              findBy: locator.findBy,
              attributes: getAllAttributes(el)
            };

            fetch('http://localhost:8080/record/log', {
              method: 'POST',
              headers: { 'Content-Type': 'application/json' },
              body: JSON.stringify(payload)
            });

            if (action === 'input' || action === 'click') {
              fetch('http://localhost:8080/record/locator', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                  name: payload.name || payload.selector,
                  lookupDetails: {
                    findBy: payload.findBy,
                    value: payload.selector
                  }
                })
              });
            }
          }

          function initializeRecorder() {
            document.addEventListener('click', function(e) {
              if (e.shiftKey) {
                send('highlight', e.target);
              } else {
                send('click', e.target);
              }
            });

            document.addEventListener('change', function(e) {
              send('input', e.target, e.target.value);
            });

            console.log('📡 Recorder initialized');
          }

          // Hook into history API
          const origPush = history.pushState;
          const origReplace = history.replaceState;

          history.pushState = function() {
            origPush.apply(this, arguments);
            reinject();
          };
          history.replaceState = function() {
            origReplace.apply(this, arguments);
            reinject();
          };
          window.addEventListener('popstate', reinject);

          // DOM observer for detecting route/component changes
          const observer = new MutationObserver((mutations) => {
            for (const m of mutations) {
              if (m.addedNodes.length > 0) {
                console.log('📦 DOM change detected. Reapplying recorder...');
                reinject();
                break;
              }
            }
          });

          observer.observe(document.body, { childList: true, subtree: true });

          let reinjectionTimer;
          function reinject() {
            clearTimeout(reinjectionTimer);
            reinjectionTimer = setTimeout(() => {
              initializeRecorder();
            }, 500);
          }

          // Run once
          initializeRecorder();
        })();
        """;
    }

    public static String getOptimizedDomExtractionScript() {
        return """
    return (function() {
        try {
            function isVisible(el) {
                if (!el) return false;
                const style = window.getComputedStyle(el);
                const rect = el.getBoundingClientRect();
                return (
                    style.display !== 'none' &&
                    style.visibility !== 'hidden' &&
                    rect.width > 0 &&
                    rect.height > 0 &&
                    el.offsetParent !== null
                );
            }

            const selectors = [
                'input',
                'textarea',
                'button',
                'select',
                'a[href]',
                '[role=button]',
                'label',
                'form',
                '[onclick]',
                '[type=submit]'
            ];

            const elements = Array.from(document.querySelectorAll(selectors.join(',')));
            const seen = new Set();
            const result = [];

            for (const el of elements) {
                if (isVisible(el)) {
                    let html = el.outerHTML.trim().replace(/\\s+/g, ' ');
                    if (!seen.has(html)) {
                        seen.add(html);
                        result.push(html);
                    }
                }
            }

            if (result.length === 0) {
                return "NO_VISIBLE_ELEMENTS_FOUND";
            }

            return result.slice(0, 100).join('\\n');

        } catch (err) {
            return "SCRIPT_ERROR: " + err.message;
        }
    })();
    """;
    }



}