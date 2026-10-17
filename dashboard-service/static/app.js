'use strict';
const $ = id => document.getElementById(id);
const colors = ['#267962', '#c6843d', '#668cba', '#9b729f', '#647959'];
const groupNames = {revenue_metrics:'Revenue',liquidity_metrics:'Liquidity',seasonality_metrics:'Seasonality',breach_persistence:'Breach persistence'};
const labels = {
 revenue_mom_growth_pct:'Revenue · month over month',revenue_yoy_growth_pct:'Revenue · year over year',
 revenue_qoq_growth_pct:'Revenue · quarter over quarter',revenue_quarter_yoy_growth_pct:'Revenue · quarter year over year',
 revenue_rolling_3m_growth_pct:'Revenue · rolling three-month growth',revenue_ttm_growth_pct:'Revenue · trailing twelve-month growth',
 overdue_ratio_pct:'Overdue receivables ratio',credit_utilisation_pct:'Credit utilisation',credit_headroom_pct:'Credit headroom',
 overdue_ratio_change_pp:'Overdue ratio · monthly change',credit_utilisation_change_pp:'Credit utilisation · monthly change',
 receivables_yoy_growth_pct:'Receivables · year over year',receivables_vs_revenue_growth_gap_pp:'Receivables vs revenue · growth gap',
 same_month_historical_deviation_pct:'Revenue · matching-month historical deviation',revenue_volatility_12m_pct:'Revenue · twelve-month volatility'
};
let token = '', report = null, selectedMetric = 'revenue_metrics.revenue_yoy_growth_pct', controller = null, sequence = 0;
const esc = value => String(value).replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const finite = v => typeof v === 'number' && Number.isFinite(v);
const number = v => finite(v) ? new Intl.NumberFormat(undefined,{maximumFractionDigits:4}).format(v) : '—';
const compact = v => new Intl.NumberFormat(undefined,{notation:'compact',maximumFractionDigits:1}).format(v);
const suffix = unit => unit === 'percent' ? '%' : unit === 'percentage_points' ? ' pp' : unit === 'months' ? ' months' : '';
const formatted = item => item && finite(item.value) ? number(item.value) + suffix(item.unit) : '—';
const monthLabel = m => new Date(m+'T00:00:00').toLocaleDateString(undefined,{month:'short',year:'2-digit'});
function name(key) {
 return labels[key] || key.replace('_consecutive_breach_months',' · consecutive breach months').replace('_breach_frequency_12m_pct',' · breach frequency, twelve months').replaceAll('_',' ').replace(/^./,c=>c.toUpperCase());
}
function element(tag, text, cls) {const node=document.createElement(tag);if(text!==undefined)node.textContent=text;if(cls)node.className=cls;return node;}
function message(text,info=false) {$('message').hidden=!text;$('message').textContent=text;$('message').className=info?'info':'';}
function resetReport(){report=null;document.body.classList.remove('has-report');$('report').hidden=true;$('empty').hidden=false;for(const id of ['metric-dialog','notes-dialog'])if($(id).open)$(id).close();}
function signout(){sequence++;controller?.abort();token='';resetReport();$('signin').hidden=false;$('logout').hidden=true;$('load-button').disabled=true;$('password').value='';$('token-input').value='';message('Signed out.');}
function signedIn(value){token=value.trim().replace(/^Bearer\s+/i,'');$('signin').hidden=true;$('logout').hidden=false;$('load-button').disabled=false;$('token-input').value='';message('Connected. Choose your MSME ID and completed reporting month.',true);}
async function api(path,options={}){
 const response=await fetch(path,{...options,cache:'no-store',headers:{...(token?{Authorization:'Bearer '+token}:{}),...options.headers}});
 const data=await response.json();
 if(!response.ok){if(response.status===401 && token)signout();throw new Error(typeof data.detail==='string'?data.detail:'Check the request values and try again.');}
 return data;
}
$('login-form').addEventListener('submit',async event=>{
 event.preventDefault();$('login-button').disabled=true;message('Signing in…',true);
 try{const data=await api('/api/dashboard/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({email:$('email').value.trim(),password:$('password').value})});signedIn(data.access_token);}
 catch(error){message(error.message);}finally{$('password').value='';$('login-button').disabled=false;}
});
$('token-form').addEventListener('submit',event=>{event.preventDefault();signedIn($('token-input').value);});
$('logout').addEventListener('click',signout);
const previous=new Date();previous.setDate(1);previous.setMonth(previous.getMonth()-1);
const defaultMonth=previous.getFullYear()+'-'+String(previous.getMonth()+1).padStart(2,'0');$('as-of').value=defaultMonth;$('as-of').max=defaultMonth;
for(const id of ['msme-id','as-of'])$(id).addEventListener('input',()=>{if(report){resetReport();message('Report settings changed. Load the new snapshot to continue.',true);}});
$('filters').addEventListener('submit',async event=>{
 event.preventDefault();if(!token){message('Sign in first.');return;}
 controller?.abort();controller=new AbortController();const current=++sequence;
 $('load-button').disabled=true;resetReport();message('Reading financial snapshots and calculating metrics…',true);
 try{
  const result=await api('/api/dashboard/msmes/'+encodeURIComponent($('msme-id').value)+'?as_of='+encodeURIComponent($('as-of').value),{signal:controller.signal});
  if(current!==sequence)return;
  report=result;document.body.classList.add('has-report');$('report').hidden=false;$('empty').hidden=true;render();message('');
 }catch(error){if(current===sequence && error.name!=='AbortError')message(error.message);}
 finally{if(current===sequence)$('load-button').disabled=!token;}
});
function rows(){return report.series.slice(-Number($('window').value));}
function value(row,id){return row.metrics[id]?.value??null;}
function metric(id){const [group,key]=id.split('.');return report.evidence[group]?.[key];}
function legend(items){return '<div class="chart-legend">'+items.map((item,i)=>'<span><svg width="9" height="9" aria-hidden="true"><circle cx="4" cy="4" r="4" fill="'+colors[i%colors.length]+'"/></svg>'+esc(item.label)+'</span>').join('')+'</div>';}
function lineChart(id,data,items,unit='',height=210){
 const target=$(id),width=Math.max(260,target.clientWidth||700);
 height=Math.max(110,Math.min(height,(target.clientHeight||height)-22));
 const pad={l:43,r:12,t:12,b:25},w=width-pad.l-pad.r,h=height-pad.t-pad.b;
 const vals=items.flatMap(item=>data.map(row=>item.get(row))).filter(finite);
 if(!vals.length){target.innerHTML='<div class="no-data">No comparable observations in this window.</div>';return;}
 let min=Math.min(0,...vals),max=Math.max(0,...vals);if(max===min)max=min+1;
 const span=max-min;max+=span*.08;if(min<0)min-=span*.06;
 const x=i=>pad.l+(data.length>1?i*w/(data.length-1):w/2),y=v=>pad.t+(max-v)/(max-min)*h;
 let svg='<svg viewBox="0 0 '+width+' '+height+'" role="img" aria-label="'+esc(target.closest('article')?.querySelector('h3')?.textContent||$('metric-title').textContent)+'"><title>'+esc(unit||'Database amount units')+'; exact values available in JSON export</title>';
 for(let i=0;i<=4;i++){const v=min+(max-min)*i/4,yy=y(v);svg+='<line class="chart-gridline" x1="'+pad.l+'" x2="'+(width-pad.r)+'" y1="'+yy+'" y2="'+yy+'"/><text class="chart-axis" x="'+(pad.l-9)+'" y="'+(yy+3)+'" text-anchor="end">'+esc(compact(v))+esc(unit)+'</text>';}
 const step=Math.max(1,Math.ceil(data.length/(width<400?4:6)));data.forEach((row,i)=>{if(i%step===0||(i===data.length-1&&(data.length-1)%step>step/2))svg+='<text class="chart-axis" x="'+x(i)+'" y="'+(height-6)+'" text-anchor="middle">'+esc(row.label||monthLabel(row.month))+'</text>';});
 items.forEach((item,index)=>{
  const color=colors[index%colors.length];let path='',active=false;
  data.forEach((row,i)=>{const v=item.get(row);if(!finite(v)){active=false;return;}path+=(active?' L':' M')+x(i)+' '+y(v);active=true;});
  svg+='<path d="'+path+'" fill="none" stroke="'+color+'" stroke-width="2.3"'+(item.dashed?' stroke-dasharray="5 5"':'')+'/>';
  data.forEach((row,i)=>{const v=item.get(row);if(finite(v))svg+='<circle class="chart-point" tabindex="0" cx="'+x(i)+'" cy="'+y(v)+'" r="5" fill="'+color+'"><title>'+esc((row.label||monthLabel(row.month))+' · '+item.label+': '+number(v)+unit)+'</title></circle>';});
 });
 target.innerHTML=svg+'</svg>'+legend(items);
}
function growthChart(){
 const names={revenue_mom_growth_pct:'Month / month',revenue_yoy_growth_pct:'Year / year',revenue_qoq_growth_pct:'Quarter / quarter',revenue_quarter_yoy_growth_pct:'Quarter / prior year',revenue_rolling_3m_growth_pct:'Rolling 3 months',revenue_ttm_growth_pct:'Trailing 12 months'};
 const data=Object.entries(report.evidence.revenue_metrics),values=data.map(([,m])=>m.value).filter(finite);
 const width=Math.max(260,$('growth-chart').clientWidth||700),height=Math.max(110,$('growth-chart').clientHeight||200),rowHeight=(height-12)/6;
 const max=Math.max(1,...values.map(Math.abs)),zero=(width+118-52)/2,scale=(width-118-52)/2/max;
 let svg='<svg viewBox="0 0 '+width+' '+height+'" role="img" aria-label="Revenue growth comparisons"><title>Revenue growth across six horizons, percentages</title><line x1="'+zero+'" x2="'+zero+'" y1="5" y2="'+(height-4)+'" stroke="#d8e4d7"/>';
 data.forEach(([key,item],i)=>{const yy=7+i*rowHeight,v=item.value;svg+='<text class="chart-axis" x="3" y="'+(yy+11)+'">'+esc(names[key])+'</text>';
 if(finite(v)){const bw=Math.abs(v)*scale;svg+='<rect x="'+(v<0?zero-bw:zero)+'" y="'+yy+'" width="'+Math.max(1,bw)+'" height="'+Math.min(18,rowHeight*.65)+'" rx="3" fill="'+(v<0?'#b87963':'#4d9177')+'"><title>'+esc(name(key)+': '+formatted(item))+'</title></rect>';}
 svg+='<text class="chart-axis" x="'+(width-2)+'" y="'+(yy+12)+'" text-anchor="end">'+esc(formatted(item))+'</text>';});
 $('growth-chart').innerHTML=svg+'</svg>';
}
function seasonalChart(){
 const years=[...new Set(report.series.filter(r=>r.available).map(r=>r.month.slice(0,4)))];
 const data=Array.from({length:12},(_,i)=>({label:new Date(2024,i,1).toLocaleDateString(undefined,{month:'short'}),index:i+1}));
 lineChart('seasonal-chart',data,years.map(year=>({label:year,get:r=>report.series.find(s=>s.month.startsWith(year+'-'+String(r.index).padStart(2,'0')))?.snapshot?.revenue??null})), '',225);
}
function breachChart(){
 const codes=['REVENUE_DETERIORATION','OVERDUE_RECEIVABLES','CREDIT_UTILISATION'],data=rows().slice(-12);
 let html='<div class="heatmap"><table><thead><tr><th>Saved indicator</th>'+data.map(r=>'<th>'+esc(new Date(r.month+'T00:00:00').toLocaleDateString(undefined,{month:'short'}))+'</th>').join('')+'</tr></thead><tbody>';
 codes.forEach(code=>{html+='<tr><th>'+esc(code==='REVENUE_DETERIORATION'?'Revenue':code==='OVERDUE_RECEIVABLES'?'Overdue':'Credit')+'</th>';
 data.forEach(row=>{const item=report.saved_indicators.find(i=>i.indicator_code===code && String(i.reporting_period).slice(0,7)===row.month.slice(0,7));const known=item && finite(item.calculated_value),breach=known && item.breached_flag==='Y';
 html+='<td class="'+(known?(breach?'breached':'clear'):'')+'" title="'+esc(monthLabel(row.month)+' · '+(known?(breach?'Breached':'Not breached'):'Unavailable'))+'">'+(known?(breach?'!':'✓'):'—')+'</td>';});html+='</tr>';});
 $('breach-chart').innerHTML=html+'</tbody></table></div><div class="heat-legend"><span>! Breached</span><span>✓ Not breached</span><span>— Unavailable</span></div>';
 const assessment=report.context.assessment;
 $('assessment-note').textContent=assessment?'Saved assessment: '+assessment.severity+(assessment.stress_pattern?' · '+assessment.stress_pattern:'')+'. Historical classification, not a new dashboard score. Saved thresholds: revenue decline '+number(assessment.revenue_decline_pct)+'%, overdue ratio '+number(assessment.overdue_receivable_pct)+'%, credit utilisation '+number(assessment.credit_utilisation_pct)+'%. These apply to their original indicator definitions, not to new growth metrics.':'No saved assessment for the selected month. No default thresholds are assumed.';
}
function renderCards(){
 const latest=report.series.at(-1).snapshot;
 const specs=[['Monthly revenue',number(latest.revenue),'Database amount units'],['Revenue · year over year',formatted(metric('revenue_metrics.revenue_yoy_growth_pct')),'Matching calendar month last year'],['Overdue receivables',formatted(metric('liquidity_metrics.overdue_ratio_pct')),'Share of total receivables'],['Credit utilisation',formatted(metric('liquidity_metrics.credit_utilisation_pct')),'Using current sanctioned limit']];
 $('cards').replaceChildren(...specs.map(([title,v,foot])=>{const node=element('article',undefined,'card');node.append(element('div',title,'card-label'),element('div',v,'card-value'),element('div',foot,'card-foot'));return node;}));
}
function renderMetrics(){
 const filter=$('metric-group').value,entries=Object.entries(report.evidence).filter(([group])=>group!=='business_context'&&(filter==='all'||group===filter)).flatMap(([group,metrics])=>Object.entries(metrics).map(([key,item])=>({id:group+'.'+key,group,key,item})));
 if(!entries.some(e=>e.id===selectedMetric))selectedMetric=entries[0].id;
 $('metric-rows').replaceChildren(...entries.map(entry=>{const tr=element('tr');if(entry.id===selectedMetric)tr.className='selected';const td=element('td'),button=element('button',name(entry.key),'metric-button');button.addEventListener('click',()=>{selectedMetric=entry.id;renderMetrics();});td.append(button);const state=element('td');state.append(element('span',entry.item.status==='available'?'Available':'Unavailable','availability '+entry.item.status));tr.append(td,element('td',formatted(entry.item)),element('td',groupNames[entry.group]),state);return tr;}));
 const [group,key]=selectedMetric.split('.'),item=metric(selectedMetric);$('metric-title').textContent=name(key);
 $('metric-select').value=selectedMetric;
 lineChart('metric-chart',rows(),[{label:name(key),get:r=>value(r,selectedMetric)}],suffix(item.unit),190);
 const details={evidence_metric_id:selectedMetric,...item};
 $('metric-details').replaceChildren(...Object.entries(details).filter(([,v])=>v!==null && v!==undefined).flatMap(([k,v])=>[element('dt',k.replaceAll('_',' ')),element('dd',typeof v==='object'?JSON.stringify(v):String(v))]));
}
function renderCharts(){
 const data=rows();
 lineChart('revenue-chart',data,[{label:'Revenue',get:r=>r.snapshot?.revenue??null}]);
 lineChart('receivables-chart',data,[{label:'Total receivables',get:r=>r.snapshot?.total_receivables??null},{label:'Overdue receivables',get:r=>r.snapshot?.overdue_receivables??null}]);
 lineChart('credit-chart',data,[{label:'Credit used',get:r=>r.snapshot?.credit_used??null},{label:'Current limit reference',get:()=>report.credit_limit,dashed:true}]);
 lineChart('ratios-chart',data,[{label:'Overdue ratio',get:r=>value(r,'liquidity_metrics.overdue_ratio_pct')},{label:'Credit utilisation',get:r=>value(r,'liquidity_metrics.credit_utilisation_pct')}],'%');
 growthChart();seasonalChart();breachChart();renderMetrics();
}
function render(){
 $('business-name').textContent=report.context.business_name;
 $('business-meta').textContent=report.context.sector+' · MSME '+report.msme_id+' · Reporting '+monthLabel(report.as_of)+' · '+report.context.history_months_available+' snapshots in the history window';
 $('metric-select').replaceChildren(...Object.entries(report.evidence).filter(([g])=>g!=='business_context').flatMap(([g,items])=>Object.keys(items).map(key=>{const option=element('option',name(key));option.value=g+'.'+key;return option;})));
 renderCards();renderCharts();
 $('limitations').replaceChildren(...[...new Set([...report.context.limitations,...report.notes])].map(note=>element('li',note)));
}
$('window').addEventListener('change',()=>{if(report)renderCharts();});
$('metric-group').addEventListener('change',()=>{if(report)renderMetrics();});
$('metric-select').addEventListener('change',()=>{if(report){selectedMetric=$('metric-select').value;$('metric-group').value='all';renderMetrics();}});
for(const button of document.querySelectorAll('[data-dialog]'))button.addEventListener('click',()=>{if(report)$(button.dataset.dialog).showModal();else message('Load a dashboard to explore its metrics and notes.',true);});
for(const button of document.querySelectorAll('[data-close]'))button.addEventListener('click',()=>$(button.dataset.close).close());
let resizeTimer;
window.addEventListener('resize',()=>{clearTimeout(resizeTimer);resizeTimer=setTimeout(()=>{if(report)renderCharts();},150);});
$('export').addEventListener('click',()=>{if(!report)return;const url=URL.createObjectURL(new Blob([JSON.stringify(report,null,2)],{type:'application/json'}));const link=element('a');link.href=url;link.download='msme-'+report.msme_id+'-'+report.as_of.slice(0,7)+'-dashboard.json';link.click();setTimeout(()=>URL.revokeObjectURL(url),1000);});
