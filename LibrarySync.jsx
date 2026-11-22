import React, {useState} from 'react';

const keys = ['planetLogs', 'planetSaved', 'planet.storybooks.v1'];
export const libraryKey = (scope, key) => scope ? 'parent:' + scope + ':' + key : key;

function local(scope, key) {
  try {
    const rows = JSON.parse(localStorage.getItem(libraryKey(scope,key)) || '[]');
    return Array.isArray(rows) ? rows : [];
  } catch {
    return [];
  }
}

function fingerprint(value) {
  return JSON.stringify(value, (name,item) => item && typeof item === 'object' && !Array.isArray(item)
    ? Object.fromEntries(Object.keys(item).sort().map(key=>[key,item[key]])) : item);
}

function merge(key, preferred, other) {
  const identity = row => key === keys[2] ? fingerprint([row?.title,row?.source,row?.pages]) : fingerprint(row);
  const counts = new Map();
  for (const entry of preferred) {
    const id = identity(entry);
    counts.set(id,(counts.get(id) || 0)+1);
  }
  return [...preferred,...other.filter(entry=>{
    const id = identity(entry);
    const count = counts.get(id) || 0;
    if (count) counts.set(id,count-1);
    return !count;
  })];
}

export default function LibrarySync({email}) {
  const [busy,setBusy] = useState(false);
  const [message,setMessage] = useState('');
  const [confirm,setConfirm] = useState(false);
  const count = keys.reduce((total,key)=>total+local(null,key).length,0);

  async function sync(mode) {
    setBusy(true);
    setMessage('');
    try {
      const response = await fetch('/api/account/library');
      if (!response.ok) throw new Error(response.status===401?'登录已过期，请重新登录。':'暂时无法读取云端资料。');
      const cloud = await response.json();
      const data = Object.fromEntries(keys.map(key=>{
        const stored = cloud.data[key] || [];
        const account = local(email,key);
        if (mode === 'download') return [key,merge(key,stored,account)];
        const source = mode === 'import' ? merge(key,account,local(null,key)) : account;
        return [key,merge(key,source,stored)];
      }));
      if (mode !== 'download') {
        const csrf = await fetch('/api/account/csrf');
        if (!csrf.ok) throw new Error('无法确认登录状态，请重试。');
        const {headerName,token} = await csrf.json();
        const saved = await fetch('/api/account/library',{
          method:'PUT', headers:{'Content-Type':'application/json',[headerName]:token},
          body:JSON.stringify({revision:cloud.revision,data})
        });
        if (saved.status===409) throw new Error('另一台设备刚更新了资料，请重试。');
        if (!saved.ok) throw new Error('资料未上传，请检查登录状态或资料大小后重试。');
      }
      for (const key of keys) localStorage.setItem(libraryKey(email,key),JSON.stringify(data[key]));

      setConfirm(false);
      setMessage(mode==='download'?'已合并云端资料。':'资料已保存到账号。');
    } catch (failure) {
      setMessage(failure instanceof TypeError?'网络暂不可用，请重试。':failure.message);
    } finally {
      setBusy(false);
    }
  }

  return <section className="book-shelf">
    <h3>跨设备同步</h3>
    <p>上传当前账号的资料，换设备后登录同一账号并选择「同步到此设备」。</p>
    <div className="guide-actions">
      <button disabled={busy} onClick={()=>sync('upload')}>上传此设备资料</button>
      <button disabled={busy} onClick={()=>sync('download')}>同步到此设备</button>
      {count>0 && <button disabled={busy} onClick={()=>setConfirm(!confirm)}>导入访客资料（{count} 条）</button>}
    </div>
    {confirm && <div>
      <p>将此浏览器访客模式中的 {count} 条收藏、记录或绘本复制到 {email}。</p>
      <button disabled={busy} onClick={()=>sync('import')}>确认导入</button>
    </div>}
    {message && <p role="status">{message}</p>}
  </section>;
}
