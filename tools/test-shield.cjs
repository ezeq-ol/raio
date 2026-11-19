const {readFileSync} = require('node:fs');
const {runInNewContext} = require('node:vm');
const assert = require('node:assert/strict');
const code = readFileSync('app/src/main/java/com/raio/browser/ShieldScript.kt', 'utf8').split('"""')[1];

function environment(hostname, showingAd, skipVisible = true) {
  const result = {styles: [], clicks: 0, observers: 0, disconnected: false};
  const skip = {disabled: false, getClientRects: () => skipVisible ? [1] : [], click: () => result.clicks++};
  const player = {querySelector: (selector) => {assert.match(selector, /skip/); return skip;}};
  result.context = {
    location: {hostname},
    window: {addEventListener: (name, fn) => {result.onPageHide = fn;}},
    document: {
      createElement: () => ({}), head: {appendChild: style => result.styles.push(style)},
      documentElement: {}, querySelector: selector => {assert.equal(selector, '.ad-showing'); return showingAd ? player : null;}
    },
    MutationObserver: class { constructor(fn) {result.onMutation = fn;} observe() {result.observers++;} disconnect() {result.disconnected = true;} },
    setTimeout: fn => {result.scheduled = fn;}
  };
  return result;
}

const ordinary = environment('example.org', false);
runInNewContext(code, ordinary.context);
assert.equal(ordinary.styles.length, 1);
assert.match(ordinary.styles[0].textContent, /ins.adsbygoogle/);
assert.equal(ordinary.observers, 0);
runInNewContext(code, ordinary.context);
assert.equal(ordinary.styles.length, 1, 'SPA re-injection must not duplicate styles');

const playback = environment('m.youtube.com', false);
runInNewContext(code, playback.context);
assert.equal(playback.clicks, 0, 'never click normal content');
assert.equal(playback.observers, 1);

const ad = environment('www.youtube.com', true);
runInNewContext(code, ad.context);
assert.equal(ad.clicks, 1, 'use only an available ad skip button');
ad.onMutation(); ad.onMutation();
assert.equal(ad.clicks, 1, 'mutations are throttled');
ad.scheduled();
assert.equal(ad.clicks, 2);
ad.onPageHide({persisted: true});
assert.equal(ad.disconnected, false, 'back-forward cache must keep filtering');
ad.onPageHide({persisted: false});
assert.equal(ad.disconnected, true);

const hidden = environment('youtube.com', true, false);
runInNewContext(code, hidden.context);
assert.equal(hidden.clicks, 0, 'never click hidden controls');
const lookalike = environment('youtube.com.example.org', true);
runInNewContext(code, lookalike.context);
assert.equal(lookalike.clicks, 0);
console.log('Shield script: 6 scenarios passed.');
