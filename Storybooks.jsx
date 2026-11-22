import React, {useEffect, useState} from 'react';
import {libraryKey} from './LibrarySync';

const symbols = {bear:'🐻', rabbit:'🐰', bird:'🐦', boat:'⛵', star:'🌟', tree:'🌳', fish:'🐟', cat:'🐱'};
const motifs = [
  ['🐷', /小猪|猪妈妈|猪爸爸|佩奇/], ['🐰', /兔|瑞贝卡/], ['🐻', /熊/], ['🐦', /鸟/],
  ['⛵', /船|帆/], ['🌟', /星/], ['🌳', /树/], ['🐟', /鱼/], ['🐱', /猫/],
  ['🌧️', /雨|泥坑/], ['🌷', /花/], ['🚗', /汽车|小车/]
];
function pageMotif(page) {
  const mentioned = motifs.filter(([,words])=>words.test(page.text));
  const selected = mentioned.find(([icon])=>icon===symbols[page.symbol]);
  return selected?.[0] || mentioned[0]?.[0] || '📖';
}
const scenes = {forest:'🌲　🌿', home:'🪴　🪟', sea:'🌊　🫧', night:'🌙　✨'};
const sample = {title:'小鸟借星光', pages:[
  {text:'天黑了，小鸟站在树枝上。回家的路在哪儿呢？',question:'小鸟在找什么？',scene:'forest',symbol:'bird'},
  {text:'一颗星星眨眨眼。小鸟抬头说：“我看见你啦！”',question:'我们也眨眨眼，好吗？',scene:'night',symbol:'star'},
  {text:'小鸟顺着星光飞呀飞。窗边亮着一盏小灯。',question:'你能用手比一比小鸟飞的样子吗？',scene:'night',symbol:'bird'},
  {text:'小鸟钻进暖暖的窝。“谢谢你，星星。晚安！”',question:'你想和谁说晚安？',scene:'home',symbol:'bird'}
]};

const shelfKey = 'planet.storybooks.v1';

function isBook(book) {
  return typeof book?.title === 'string' && book.title.trim() &&
    Array.isArray(book.pages) && book.pages.length === 4 && book.pages.every(page =>
      typeof page?.text === 'string' && page.text.trim() &&
      typeof page.question === 'string' && page.question.trim() &&
      Object.hasOwn(symbols,page.symbol) && Object.hasOwn(scenes,page.scene));
}

function readShelf(key) {
  try {
    const books = JSON.parse(localStorage.getItem(key) || '[]');
    return Array.isArray(books) ? books.filter(book =>
      isBook(book) && typeof book.source === 'string' &&
      Number.isInteger(book.page) && book.page >= 0 && book.page < book.pages.length) : [];
  } catch {
    return [];
  }
}

function sameBook(left,right) {
  return left.source === right.source && left.title === right.title &&
    JSON.stringify(left.pages) === JSON.stringify(right.pages);
}

export default function Storybooks({scope}) {
  const storageKey = libraryKey(scope,shelfKey);
  const [cartoon,setCartoon] = useState('');
  const [episode,setEpisode] = useState('');
  const [synopsis,setSynopsis] = useState('');
  const [book,setBook] = useState(null);
  const [page,setPage] = useState(0);
  const [busy,setBusy] = useState(false);
  const [error,setError] = useState('');
  const [shelf,setShelf] = useState(()=>readShelf(storageKey));
  const saved = book && shelf.some(entry=>sameBook(entry,book));
  const [controller] = useState(()=>new AbortController());
  useEffect(()=>()=>controller.abort(),[controller]);

  function read(data,source) {
    const selected = {title:data.title, pages:data.pages, source};
    const previous = shelf.find(entry=>sameBook(entry,selected));
    setBook(selected);
    setPage(previous?.page ?? 0);
    setError('');
  }
  function updateShelf(nextPage, remove = false) {
    try {
      const otherBooks = readShelf(storageKey).filter(entry=>!sameBook(entry,book));
      const books = remove ? otherBooks : [{...book,page:nextPage},...otherBooks];
      localStorage.setItem(storageKey,JSON.stringify(books));
      setShelf(books);
      setError('');
    } catch {
      setError('浏览器无法保存书架，请检查存储设置后重试。');
    }
  }
  function turn(nextPage) {
    setPage(nextPage);
    if (saved) updateShelf(nextPage);
  }
  async function generate(event) {
    event.preventDefault();
    if (busy || !cartoon.trim()) return;
    setBusy(true);
    setError('');
    try {
      const response = await fetch('/api/storybooks',{
        method:'POST', headers:{'Content-Type':'application/json'}, signal:controller.signal,
        body:JSON.stringify({cartoon:cartoon.trim(),episode:episode.trim(),synopsis:synopsis.trim()})
      });
      if (!response.ok) {
        const messages = {400:'请检查片名和输入长度。',429:'请求较多，请稍后再试。',503:'绘本生成服务暂不可用，请检查模型密钥和账户余额。'};
        throw new Error(messages[response.status] || '这次没能生成完整绘本，请重试。');
      }
      const data = await response.json();
      if (!isBook(data))
        throw new Error('绘本内容不完整，请重新生成。');
      read(data,'《'+cartoon.trim()+'》'+(episode.trim()?' · '+episode.trim():''));
    } catch (failure) {
      if (!controller.signal.aborted) setError(failure instanceof TypeError?'暂时无法连接，请稍后重试。':failure.message);
    } finally {
      if (!controller.signal.aborted) setBusy(false);
    }
  }
  if (book) {
    const current = book.pages[page];
    return <section className="book-panel">
      <small>{book.source}</small><h2>{book.title}</h2>
      <article aria-live="polite">
        <div className={'book-picture '+current.scene} aria-hidden="true"><span>{scenes[current.scene]}</span><strong>{pageMotif(current)}</strong></div>
        <small>第 {page+1} / {book.pages.length} 页</small><p className="read-aloud">{current.text}</p>
        <div className="book-question"><b>一起聊聊</b><p>{current.question}</p></div>
      </article>
      <div className="guide-actions"><button disabled={!page} onClick={()=>turn(page-1)}>上一页</button>
        {page<book.pages.length-1 ? <button className="yellow" onClick={()=>turn(page+1)}>下一页</button>
          : <button className="yellow" onClick={()=>turn(0)}>再读一遍</button>}
        <button onClick={()=>updateShelf(page, saved)}>{saved?'从书架移除':'保存到书架'}</button>
        <button onClick={()=>setBook(null)}>返回创作</button></div>
      {error && <p role="alert">{error}</p>}
    </section>;
  }
  return <section className="book-panel"><h2>动画绘本</h2>
    <p>把喜欢的动画变成四页原创小故事，每页都有一句话题，陪宝宝边读边聊。</p>
    <form onSubmit={generate}><fieldset className="activity-options" disabled={busy}>
      <label>动画片名称<input required maxLength={100} value={cartoon} onChange={e=>setCartoon(e.target.value)} placeholder="例如：小猪佩奇"/></label>
      <label>集数（选填）<input maxLength={40} value={episode} onChange={e=>setEpisode(e.target.value)} placeholder="例如：第一季第 3 集"/></label>
      <label>想聊的情节（选填）<textarea maxLength={1000} value={synopsis} onChange={e=>setSynopsis(e.target.value)} placeholder="想贴近某一集？写下你们看过的一幕。"/></label>
    </fieldset><div className="guide-actions"><button className="yellow" disabled={busy||!cartoon.trim()}>{busy?'正在编绘故事…':'生成绘本'}</button>
      <button type="button" disabled={busy} onClick={()=>read(sample,'原创示例')}>试读示例</button></div>
      {error && <p role="alert">{error}</p>}</form>
    <section className="book-shelf">
      <h3>我的书架</h3>
      {shelf.length === 0 ? <p>阅读时点「保存到书架」，下次就能接着读。</p> :
        <ul>{shelf.map(entry=><li key={JSON.stringify([entry.title,entry.source,entry.pages])}>
          <div><strong>{entry.title}</strong><span>{entry.source} · 第 {entry.page+1} 页</span></div>
          <button type="button" disabled={busy} onClick={()=>read(entry,entry.source)}
            aria-label={'继续阅读 '+entry.title}>继续阅读</button>
        </li>)}</ul>}
    </section>
  </section>;
}
