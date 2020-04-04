(ns html-tools.snippets.preloader)


(def css-code
  ".preloader {margin: 45vh auto 0; width: 66px; height: 12px;}
div.preloader div {color: #000; margin: 5px 0; text-transform: uppercase; font-family: 'Arial', sans-serif; font-size: 9px; letter-spacing: 2px;}
.preloader .line {width: 1px; height: 12px; background: #000; margin: 0 1px; display: inline-block; animation: opacity-1 1000ms infinite ease-in-out;}
.preloader .line-1 { animation-delay: 800ms; }
.preloader .line-2 { animation-delay: 600ms; }
.preloader .line-3 { animation-delay: 400ms; }
.preloader .line-4 { animation-delay: 200ms; }
.preloader .line-6 { animation-delay: 200ms; }
.preloader .line-7 { animation-delay: 400ms; }
.preloader .line-8 { animation-delay: 600ms; }
@keyframes opacity-1 {0% {opacity: 1;} 50% {opacity: 0;} 100% {opacity: 1;}}
@keyframes opacity-2 {0% {opacity: 1; height: 15px;} 50% {opacity: 0; height: 12px;} 100% {opacity: 1; height: 15px;}}")

(def html-code
  "<div class='preloader'> <div></div> <span class='line line-1'></span> <span class='line line-2'></span> <span class='line line-3'></span> <span class='line line-4'></span> <span class='line line-5'></span> <span class='line line-6'></span> <span class='line line-7'></span> <span class='line line-8'></span> </div>")
