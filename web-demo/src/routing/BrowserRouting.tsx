import {createContext,useCallback,useContext,useLayoutEffect,useRef,useState,type ReactNode} from 'react';
import {canLeaveDemand} from '../modules/cooperation/navigation';
import {readRoute,routeHref,type AppRoute} from './routes';
const key='__utbridora_route_v1';
type Entry={route:AppRoute;href:string;position:number};
type Options={replace?:boolean;guard?:boolean};
type Router={route:AppRoute;go:(route:AppRoute,options?:Options)=>boolean};
const Context=createContext<Router|null>(null);
function position(state:unknown):number|null {
  const n=state && typeof state==='object'?(state as Record<string,unknown>)[key]:null;
  return typeof n==='number' && Number.isSafeInteger(n) && n>=0?n:null;
}
const stateAt=(n:number)=>({...((history.state && typeof history.state==='object')?history.state:{}),[key]:n});
function current():Entry {
  const route=readRoute(location.pathname,location.search);
  return {route,href:route.kind==='not-found'?location.pathname+location.search:routeHref(route),position:position(history.state) ?? 0};
}
/** URL is the navigation source of truth; route state never contains an authentication token. */
export function BrowserRouting({children}:{children:ReactNode}) {
  const [entry,setEntry]=useState(current),active=useRef(entry),restoring=useRef(false);
  useLayoutEffect(()=>{
    history.replaceState(stateAt(active.current.position),'',active.current.href);
    const pop=()=>{
      const previous=active.current,next=current();
      if(restoring.current){
        const delta=previous.position-next.position;
        if(delta)history.go(delta);else restoring.current=false;
        return;
      }
      if(next.href===previous.href)return;
      if(!canLeaveDemand()){
        const delta=previous.position-next.position;
        if(delta){restoring.current=true;history.go(delta);}
        else history.replaceState(stateAt(previous.position),'',previous.href);
        return;
      }
      active.current=next;
      history.replaceState(stateAt(next.position),'',next.href);setEntry(next);
    };
    window.addEventListener('popstate',pop);return()=>window.removeEventListener('popstate',pop);
  },[]);
  const go=useCallback((route:AppRoute,{replace=false,guard=true}:Options={})=>{
    if(restoring.current)return false;
    const href=routeHref(route),previous=active.current;
    if(href===previous.href)return true;
    if(guard && !canLeaveDemand())return false;
    const url=new URL(href,location.origin);
    const next={route:readRoute(url.pathname,url.search),href,position:previous.position+(replace?0:1)};
    history[replace?'replaceState':'pushState'](stateAt(next.position),'',href);
    active.current=next;setEntry(next);return true;
  },[]);
  useLayoutEffect(()=>{
    const click=(event:MouseEvent)=>{
      if(event.defaultPrevented || event.button!==0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey)return;
      const link=event.target instanceof Element?event.target.closest<HTMLAnchorElement>('a[href]'):null;
      if(!link || link.hasAttribute('download') || link.target && link.target!=='_self')return;
      const url=new URL(link.href,location.href);
      if(url.origin!==location.origin || url.hash)return; // Mail-token fragments and skip links retain their native behavior.
      const target=readRoute(url.pathname,url.search);
      if(target.kind==='not-found')return; // Asset/API/download URLs are not application routes.
      event.preventDefault();go(target);
    };
    document.addEventListener('click',click);return()=>document.removeEventListener('click',click);
  },[go]);
  return <Context.Provider value={{route:entry.route,go}}>{children}</Context.Provider>;
}
/** Components rendered in isolated test fixtures keep their existing local navigation. */
export const useBrowserRouting=()=>useContext(Context);
export function useViewRouting(screen:string) {
  const router=useBrowserRouting();
  return router?.route.kind==='workspace' && router.route.screen===screen?{route:router.route,go:router.go}:null;
}
