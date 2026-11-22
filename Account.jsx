import React, {useEffect, useState} from 'react';
import LibrarySync from './LibrarySync';

export default function Account({onAccount}) {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [register, setRegister] = useState(false);
  const [account, setAccount] = useState(null);
  const [busy, setBusy] = useState(true);
  const [message, setMessage] = useState('');
  const [requestReset, setRequestReset] = useState(false);
  const [resetToken,setResetToken] = useState(()=>new URLSearchParams(window.location.hash.slice(1)).get('reset-password'));

  useEffect(() => {
    const request = new AbortController();
    fetch('/api/account', {signal:request.signal})
      .then(async response => {
        if (response.ok) setAccount(await response.json());
        else if (response.status !== 401) throw new Error();
      })
      .catch(() => {
        if (!request.signal.aborted) setMessage('暂时无法读取登录状态。');
      })
      .finally(() => {
        if (!request.signal.aborted) setBusy(false);
      });
    return () => request.abort();
  }, []);

  async function submit(event) {
    event.preventDefault();
    if (busy) return;
    setBusy(true);
    setMessage('');
    try {
      const tokenResponse = await fetch('/api/account/csrf');
      if (!tokenResponse.ok) throw new Error('暂时无法连接账号服务。');
      const {headerName, token} = await tokenResponse.json();
      const headers = {[headerName]:token};
      let response;
      if (resetToken || requestReset) {
        const endpoint = '/api/account/password-reset' + (resetToken?'/confirm':'');
        const payload = resetToken ? {token:resetToken,password} : {email};
        response = await fetch(endpoint,{
          method:'POST', headers:{...headers,'Content-Type':'application/json'}, body:JSON.stringify(payload)
        });
        if (response.status === 503) throw new Error('密码重置邮件暂不可用，请稍后重试。');
        if (response.status === 429) throw new Error('操作过于频繁，请稍后重试。');
        if (!response.ok) throw new Error(resetToken?'链接已失效，或新密码格式不正确。':'请检查邮箱地址。');
        if (resetToken) {
          window.history.replaceState(null,'',window.location.pathname + window.location.search);
          setResetToken(null);
          setAccount(null);
          onAccount(null);
          setMessage('密码已重置，请重新登录。');
        } else {
          setRequestReset(false);
          setMessage('如果邮箱已注册，你会收到重置链接。');
        }
      } else if (account) {
        response = await fetch('/api/account/logout', {method:'POST', headers});
        if (!response.ok) throw new Error('退出失败，请重试。');
        setAccount(null);
        onAccount(null);
      } else if (register) {
        response = await fetch('/api/account/register', {
          method:'POST', headers:{...headers,'Content-Type':'application/json'},
          body:JSON.stringify({email,password})
        });
        if (response.status === 409) throw new Error('该邮箱已注册，请直接登录。');
        if (response.status === 429) throw new Error('操作过于频繁，请稍后重试。');
        if (!response.ok) throw new Error('请检查邮箱和密码长度。');
        setRegister(false);
        setMessage('注册成功，请登录。');
      } else {
        response = await fetch('/api/account/login', {
          method:'POST', headers, body:new URLSearchParams({email,password})
        });
        if (response.status === 401) throw new Error('邮箱或密码不正确。');
        if (response.status === 429) throw new Error('操作过于频繁，请稍后重试。');
        if (!response.ok) throw new Error('登录失败，请重试。');
        const profile = await fetch('/api/account');
        if (!profile.ok) throw new Error('登录状态读取失败，请刷新页面。');
        const user = await profile.json();
        setAccount(user);
        onAccount(user.email);
      }
      setPassword('');
    } catch (error) {
      setMessage(error instanceof TypeError ? '暂时无法连接账号服务。' : error.message);
    } finally {
      setBusy(false);
    }
  }

  return <section className="book-panel">
    <h2>家长账号</h2>
    <form onSubmit={submit}>
      {account && !resetToken ? <p>{account.email}</p> :
        <fieldset className="activity-options" disabled={busy}>
          <legend>{resetToken || requestReset ? '重置密码' : register ? '注册' : '登录'}</legend>
          {!resetToken && <label>邮箱<input type="email" required maxLength={254} autoComplete="email" value={email}
            onChange={event=>setEmail(event.target.value)}/></label>}
          {!requestReset && <label>{resetToken?'新密码':'密码'}<input type="password" required minLength={register||resetToken?12:undefined} maxLength={72}
            autoComplete={register||resetToken?'new-password':'current-password'} value={password}
            onChange={event=>setPassword(event.target.value)}/></label>}
        </fieldset>}
      <div className="guide-actions">
        <button className="yellow" disabled={busy}>{resetToken?'保存新密码':requestReset?'发送重置邮件':account?'退出登录':register?'创建账号':'登录'}</button>
        {!account && !requestReset && !resetToken && <button type="button" disabled={busy} onClick={()=>{
          setRegister(!register); setPassword(''); setMessage('');
        }}>{register?'返回登录':'注册账号'}</button>}
        {!account && !register && !resetToken && <button type="button" disabled={busy} onClick={()=>{
          setRequestReset(!requestReset); setPassword(''); setMessage('');
        }}>{requestReset?'返回登录':'忘记密码'}</button>}
      </div>
      {message && <p role="status">{message}</p>}
    </form>
    {account && !resetToken && <LibrarySync email={account.email}/>}
  </section>;
}
