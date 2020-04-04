(ns html-tools.snippets.error-alert)


(def script "
  window.onerror = function(msg, url, line, col, error) {
    var extra = !col ? '' : '\\ncolumn: ' + col;
    extra += !error ? '' : '\\nerror: ' + error;
    alert('Error: ' + msg + '\\nurl: ' + url + '\\nline: ' + line + extra);
    return true;
  };
")
