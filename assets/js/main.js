/* VITC — Vanilla JS */
(function(){
  // Loader
  window.addEventListener('load',()=>{
    const l=document.getElementById('loader');
    if(l) setTimeout(()=>l.classList.add('hidden'),600);
  });

  // Header scroll
  const header=document.querySelector('.header');
  const onScroll=()=>{
    if(header) header.classList.toggle('scrolled',window.scrollY>40);
    const p=document.getElementById('scroll-progress');
    if(p){
      const h=document.documentElement.scrollHeight-window.innerHeight;
      p.style.width=(window.scrollY/h*100)+'%';
    }
    const t=document.getElementById('backTop');
    if(t) t.classList.toggle('show',window.scrollY>500);
  };
  window.addEventListener('scroll',onScroll,{passive:true});
  onScroll();

  // Hamburger
  const burger=document.querySelector('.hamburger');
  const links=document.querySelector('.nav-links');
  if(burger&&links){
    burger.addEventListener('click',()=>{
      burger.classList.toggle('open');
      links.classList.toggle('open');
    });
    links.querySelectorAll('a').forEach(a=>a.addEventListener('click',()=>{
      burger.classList.remove('open');links.classList.remove('open');
    }));
  }

  // Reveal on scroll
  const io=new IntersectionObserver(entries=>{
    entries.forEach(e=>{if(e.isIntersecting){e.target.classList.add('in');io.unobserve(e.target);}});
  },{threshold:.12});
  document.querySelectorAll('.reveal').forEach(el=>io.observe(el));

  // Counters
  const counters=document.querySelectorAll('[data-count]');
  const cio=new IntersectionObserver(entries=>{
    entries.forEach(e=>{
      if(!e.isIntersecting) return;
      const el=e.target,to=parseInt(el.dataset.count,10),dur=1600;
      let start=null;
      const step=(ts)=>{
        if(!start) start=ts;
        const p=Math.min((ts-start)/dur,1);
        el.textContent=Math.floor(p*to).toLocaleString()+(el.dataset.suffix||'');
        if(p<1) requestAnimationFrame(step);
      };
      requestAnimationFrame(step);
      cio.unobserve(el);
    });
  },{threshold:.4});
  counters.forEach(c=>cio.observe(c));

  // Typing effect
  const typed=document.querySelector('[data-typed]');
  if(typed){
    const words=JSON.parse(typed.dataset.typed);
    let wi=0,ci=0,del=false;
    const tick=()=>{
      const w=words[wi];
      typed.textContent=w.slice(0,ci);
      if(!del&&ci<w.length){ci++;setTimeout(tick,90);}
      else if(del&&ci>0){ci--;setTimeout(tick,45);}
      else{del=!del;if(!del){wi=(wi+1)%words.length;}setTimeout(tick,900);}
    };
    tick();
  }

  // FAQ
  document.querySelectorAll('.faq-q').forEach(q=>{
    q.addEventListener('click',()=>q.parentElement.classList.toggle('open'));
  });

  // Form validation
  document.querySelectorAll('form[data-validate]:not([data-api])').forEach(f=>{
    f.addEventListener('submit',e=>{
      e.preventDefault();
      let ok=true;
      f.querySelectorAll('[required]').forEach(fld=>{
        const wrap=fld.closest('.field');
        const v=fld.value.trim();
        let bad=!v;
        if(fld.type==='email') bad=bad||!/^\S+@\S+\.\S+$/.test(v);
        if(fld.type==='tel') bad=bad||!/^[0-9+\-\s()]{7,15}$/.test(v);
        wrap.classList.toggle('invalid',bad);
        if(bad) ok=false;
      });
      if(ok){
        const msg=f.querySelector('.form-msg');
        if(msg){msg.textContent='✓ Thank you! Our counsellor will contact you within 24 hours.';msg.style.color='#0a8a3d';}
        f.reset();
      }
    });
    f.querySelectorAll('input,select,textarea').forEach(i=>{
      i.addEventListener('input',()=>i.closest('.field')?.classList.remove('invalid'));
    });
  });

  // Gallery filter + lightbox
  const gitems=document.querySelectorAll('.gallery .g-item');
  document.querySelectorAll('.filters button').forEach(b=>{
    b.addEventListener('click',()=>{
      document.querySelectorAll('.filters button').forEach(x=>x.classList.remove('active'));
      b.classList.add('active');
      const c=b.dataset.filter;
      gitems.forEach(g=>{g.style.display=(c==='all'||g.dataset.cat===c)?'block':'none';});
    });
  });
  const lb=document.getElementById('lightbox');
  if(lb){
    gitems.forEach(g=>g.addEventListener('click',()=>{
      const img=g.querySelector('img');
      lb.querySelector('img').src=img.src;
      lb.classList.add('open');
    }));
    lb.addEventListener('click',e=>{if(e.target===lb||e.target.classList.contains('close')) lb.classList.remove('open');});
  }

  // Courses filter/search
  const search=document.getElementById('courseSearch');
  const cfilters=document.querySelectorAll('[data-cfilter]');
  const cards=document.querySelectorAll('[data-course]');
  const applyC=()=>{
    const q=(search?.value||'').toLowerCase();
    const active=document.querySelector('[data-cfilter].active')?.dataset.cfilter||'all';
    cards.forEach(c=>{
      const match=(active==='all'||c.dataset.course===active)&&c.textContent.toLowerCase().includes(q);
      c.style.display=match?'':'none';
    });
  };
  cfilters.forEach(b=>b.addEventListener('click',()=>{
    cfilters.forEach(x=>x.classList.remove('active'));b.classList.add('active');applyC();
  }));
  search?.addEventListener('input',applyC);

  // Popup after 8s (only home)
  const popup=document.getElementById('popup');
  if(popup && !sessionStorage.getItem('vitc_pop')){
    setTimeout(()=>{popup.classList.add('open');sessionStorage.setItem('vitc_pop','1');},9000);
    popup.addEventListener('click',e=>{if(e.target===popup||e.target.classList.contains('p-close')) popup.classList.remove('open');});
  }

  // Back to top
  document.getElementById('backTop')?.addEventListener('click',()=>window.scrollTo({top:0,behavior:'smooth'}));

  // Newsletter dummy
  document.querySelector('.newsletter')?.addEventListener('submit',e=>{
    e.preventDefault();alert('Thanks for subscribing! 🎉');e.target.reset();
  });

  // Active nav
  const path=location.pathname.split('/').pop()||'index.html';
  document.querySelectorAll('.nav-links a').forEach(a=>{
    if((a.getAttribute('href')||'')===path) a.classList.add('active');
  });
})();
