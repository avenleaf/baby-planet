import {libraryKey} from './LibrarySync';
import Account from './Account';
import Storybooks from './Storybooks';
import {hasSteps, readActivities, sameActivity} from './journal.mjs';
import React,{useEffect,useState} from 'react';import{createRoot}from'react-dom/client';import{Planet,Crew}from'./art';import './style.css';
import {agentNames as agents, activities} from "./activities.mjs";
function PlayGuide({guide,isSaved,toggleSaved,finish}) {
  const [step,setStep] = useState(null);
  const [note,setNote] = useState('');
  const last = guide.steps.length - 1;
  if (step !== null) {
    return <>
      <section className="play-step" aria-live="polite">
        <strong>第 {step + 1} 步，共 {guide.steps.length} 步</strong>
        <progress value={step + 1} max={guide.steps.length} aria-label="带玩进度"/>
        <p>{guide.steps[step]}</p>
      </section>
      {step === last && <label>宝宝的反应（选填）
        <textarea maxLength={300} value={note} onChange={event=>setNote(event.target.value)}
          placeholder="例如：宝宝自己找到了红色玩具，还想再藏一次。"/>
      </label>}
      <div className="guide-actions">
        <button onClick={()=>setStep(step===0 ? null : step-1)}>{step===0?'返回总览':'上一步'}</button>
        {step===last ? <button className="yellow" onClick={()=>finish(note)}>完成活动并记录</button>
          : <button className="yellow" onClick={()=>setStep(step+1)}>下一步</button>}
      </div>
    </>;
  }
  return <>
    <ol>{guide.steps.map((text,index)=><li key={index}>{text}</li>)}</ol>
    <div className="guide-actions">
      <button className="yellow" onClick={()=>setStep(0)}>开始分步带玩</button>
      <button aria-pressed={!!isSaved} onClick={toggleSaved}>{isSaved?'已收藏 · 取消':'收藏活动'}</button>
      <button onClick={()=>finish(note)}>直接记录完成</button>
    </div>
  </>;
}
function App() {
  const [tab,T]=useState(()=>window.location.hash.startsWith('#reset-password=')?'家长账号':'探索大厅'), [sel,S]=useState(null), [guide,G]=useState(null);
  const [topic,Q]=useState(''), [busy,B]=useState(false), [error,E]=useState('');
  const [minutes,setMinutes]=useState(5), [materials,setMaterials]=useState('');
  const [scope,setScope]=useState(null);
  const [logs,L]=useState([]);
  const [saved,setSaved]=useState([]);
  useEffect(()=>{
    L(readActivities(libraryKey(scope,'planetLogs')));
    setSaved(readActivities(libraryKey(scope,'planetSaved')));
  },[scope,tab]);
  function loadLibrary(email) {
    setScope(email);
    L(readActivities(libraryKey(email,'planetLogs')));
    setSaved(readActivities(libraryKey(email,'planetSaved')));
  }
  useEffect(()=>{
    let active = true;
    fetch('/api/account').then(response=>response.ok?response.json():null)
      .catch(()=>null).then(account=>{if(active) loadLibrary(account?.email || null);});
    return ()=>{active=false;};
  },[]);
  const [shelf,setShelf]=useState('完成记录');
  const activity = guide && {title:guide.title, steps:guide.steps, i:sel};
  const isSaved = activity && saved.some(row=>sameActivity(row, activity));
  function open(i, selected=null) {
    S(i); G(selected); Q(''); E(''); setMinutes(5); setMaterials('');
  }
  function continueActivity(row) {
    open(row.i);
    const observation = typeof row.note === 'string' && row.note.trim()
      ? '宝宝上次的反应：' + row.note.trim() : '';
    Q(['上次玩了《' + row.title + '》。', observation, '请用一个新的玩法延续这个主题。'].filter(Boolean).join('\n'));
  }
  function persist(key, rows, update) {
    try {
      localStorage.setItem(key, JSON.stringify(rows));
      update(rows);
      E('');
      return true;
    } catch {
      E('浏览器无法保存记录，请检查存储空间或浏览器设置。');
      return false;
    }
  }
  function toggleSaved() {
    const rows = isSaved ? saved.filter(row=>!sameActivity(row, activity)) : [activity,...saved];
    persist(libraryKey(scope,'planetSaved'),rows,setSaved);
  }
  function finish(note) {
    const entry = {...activity,note:note.trim(),date:new Date().toLocaleDateString('zh-CN')};
    if (persist(libraryKey(scope,'planetLogs'),[entry,...logs],L)) {
      S(null); setShelf('完成记录'); T('成长记录');
    }
  }
async function generate(){B(true);E('');try{let res=await fetch('/api/activities',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({agent:sel,source:topic.trim(),minutes,materials:materials.trim()})});if(!res.ok){E(({429:'请求较多，请稍后再试。',503:'AI 生成服务暂不可用，请检查 DeepSeek 密钥和账户余额。',502:'暂未获得完整活动，请重新生成。'})[res.status]||'活动生成失败，请稍后再试。');return;}let data=await res.json();G(data)}catch{E('AI 服务暂未连接，可以先体验下方示例活动。')}finally{B(false)}}let explore=()=>document.getElementById('agents')?.scrollIntoView({behavior:'smooth'});return <><header><div className="bar"><div className="brand"><span>🪐</span> 小芽星球</div><nav>{['探索大厅','动画绘本','成长记录'].map((t,i)=><button className={tab===t?'active':''} onClick={()=>T(t)} key={t}>{['✦','▤','◷'][i]}　{t}</button>)}</nav><button className="parent" aria-label="家长账号" onClick={()=>T('家长账号')}>🐻 <b>家长账号</b></button></div></header><main>{tab==='探索大厅'?<><section className="hero"><div className="eyebrow">✧　AI 亲子启蒙空间</div><h1>小小好奇心，<br/><em>大大宇宙梦</em></h1><p>和 AI 启蒙伙伴一起，把知识变成好玩的亲子游戏。</p><button className="yellow" onClick={explore}>✦　探索启蒙星球</button><Crew/></section><section id="agents"><small>✦　LEARNING PLANETS</small><h2>启蒙智能体</h2><p>选择一颗星球，开启专属亲子启蒙之旅</p><div className="grid">{agents.map((a,i)=><button className={'card c'+i} key={a[0]} onClick={()=>open(i)}><Planet n={i}/><label>{a[1]}</label><h3>{a[0]}</h3><p>{a[2]}</p><strong>进入星球　›</strong></button>)}</div></section><section className="today"><span className="bulb">💡</span><div><small>DAILY INSPIRATION</small><h3>今日亲子灵感</h3><p>从一个颜色小游戏开始，让英语自然融入生活。</p></div><button onClick={()=>{open(0,activities[0][0])}}>看看怎么玩　›</button></section></>:tab==='动画绘本'?<Storybooks key={scope} scope={scope}/>:tab==='家长账号'?<Account onAccount={loadLibrary}/>:<section><small>✦　GROWTH JOURNAL</small><h2>成长记录</h2>
<div className="journal-tabs" aria-label="记录类型">
  {['完成记录','我的收藏'].map(name=><button key={name} aria-pressed={shelf===name} onClick={()=>setShelf(name)}>
    {name} · {name==='完成记录'?logs.length:saved.length}
  </button>)}
</div>
{(shelf==='完成记录'?logs:saved).map((row,index)=>
  <article className="record journal-row" key={index}>
    <div><small>{agents[row.i][0]}</small><h3>{row.title}</h3>
      {row.date && <time>{row.date}</time>}{typeof row.note==="string" && row.note && <p className="observation"><b>带玩观察</b> {row.note}</p>}
    </div>
    <div className="record-actions">{hasSteps(row) ? <button onClick={()=>open(row.i,row)}>查看步骤</button> : <span>旧记录仅保存了活动名称</span>}{shelf==='完成记录' && <button onClick={()=>continueActivity(row)}>设计下次活动</button>}</div>
  </article>
)}
{shelf==='完成记录' && !logs.length && <p>完成一次亲子活动后，就能在这里回顾你们的探索足迹。</p>}
{shelf==='我的收藏' && !saved.length && <p>在活动步骤页点「收藏活动」，下次就能从这里继续玩。</p>}</section>}</main>{sel!==null&&<dialog open aria-label="亲子启蒙活动"><div className="sheet"><button aria-label="关闭" className="close" disabled={busy} onClick={()=>S(null)}>×</button><small>{agents[sel][1]} PLANET</small><h2>{guide?guide.title:agents[sel][0]}</h2>{guide?<><PlayGuide guide={guide} isSaved={isSaved} toggleSaved={toggleSaved} finish={finish}/>{error && <p role="alert">{error}</p>}</>:<><label htmlFor="source">宝宝喜欢什么？也可以粘贴绘本、动画情节或早教资料。</label><textarea id="source" maxLength={4000} value={topic} onChange={e=>Q(e.target.value)} placeholder="例如：宝宝喜欢小汽车，想用颜色玩个小游戏"/><fieldset className="activity-options" disabled={busy}>
<legend>这次准备怎么玩？</legend>
<label>带玩时长 <select value={minutes} onChange={e=>setMinutes(Number(e.target.value))}>
{[3,5,10].map(value=><option key={value} value={value}>{value} 分钟</option>)}</select></label>
<label>手边材料（选填）<input value={materials} maxLength={150} onChange={e=>setMaterials(e.target.value)} placeholder="例如：一个玩偶，或不用道具"/></label>
</fieldset><button className="yellow" disabled={!topic.trim()||busy} onClick={generate}>{busy?'正在设计活动…':'生成亲子活动'}</button>{error&&<p role="alert">{error}</p>}<hr/><section className="activity-library" aria-label="亲子活动库">
<h3>按手边的材料选活动</h3>
{activities[sel].map(item=><button key={item.title} disabled={busy} onClick={()=>open(sel,item)}>
  <strong>{item.title}</strong><span>{item.materials}</span>
</button>)}
</section></>}</div></dialog>}</>}createRoot(document.getElementById('root')).render(<App/>);
