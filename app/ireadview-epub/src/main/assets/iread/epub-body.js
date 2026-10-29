/* iRead-owned document renderer. ES5 syntax for API21 system WebViews. */
(function (config) {
    'use strict';
    var body = document.body, root = document.documentElement;
    // Native viewport avoids innerWidth expanding to an oversized image before the first reflow.
    var width = config.width, height = config.height;
    var paged = config.paged;
    var fixed = config.fixed;
    var style = document.createElement('style');
    style.textContent = (fixed ? '' : config.css) + '\n' +
        'html{margin:0!important;padding:0!important;overflow:hidden!important;}' +
        'body{margin:0!important;padding:0!important;box-sizing:border-box!important;}' +
        (fixed ? '' : 'img,svg,video{max-width:100%!important;object-fit:contain;} img{height:auto!important;width:auto;max-height:' + height + 'px!important;} figure{max-width:100%!important;}') +
        (paged ? 'html{width:' + width + 'px!important;height:' + height + 'px!important;}' +
            'body{width:' + width + 'px!important;height:' + height + 'px!important;' +
            '-webkit-column-width:' + width + 'px!important;column-width:' + width + 'px!important;' +
            '-webkit-column-gap:0!important;column-gap:0!important;column-fill:auto!important;' +
            'overflow:visible!important;}' : 'html{overflow-y:auto!important;}body{height:auto!important;}');
    document.head.appendChild(style);
    if (fixed) {
        var viewport = (root.getAttribute('data-iread-viewport') || 'width=' + width + ',height=' + height);
        var vw = /width\s*=\s*([\d.]+)/i.exec(viewport), vh = /height\s*=\s*([\d.]+)/i.exec(viewport);
        var fw = vw ? parseFloat(vw[1]) : width, fh = vh ? parseFloat(vh[1]) : height;
        var scale = Math.min(width / fw, height / fh);
        style.textContent = 'html{width:' + width + 'px!important;height:' + height + 'px!important;margin:0!important;padding:0!important;overflow:hidden!important;}' +
            'body{position:absolute!important;margin:0!important;padding:0!important;width:' + fw + 'px!important;height:' + fh + 'px!important;' +
            'left:' + ((width-fw*scale)/2) + 'px!important;top:' + ((height-fh*scale)/2) + 'px!important;' +
            'transform-origin:0 0!important;transform:scale(' + scale + ')!important;}';
    }
    function path(node) {
        if (node && node.__ireadPath) return node.__ireadPath;
        var result = [];
        while (node && node !== body) {
            var parent = node.parentNode;
            if (!parent) return null;
            result.unshift(Array.prototype.indexOf.call(parent.childNodes, node));
            node = parent;
        }
        return node === body ? result : null;
    }
    // Keep canonical text addresses when our inline highlight spans split DOM text nodes.
    // Publication node paths/offsets never refer to the transient decoration wrappers.
    var textNodes = [], initialWalker = document.createTreeWalker(body, 4, null, false), initialNode;
    while ((initialNode = initialWalker.nextNode())) {
        initialNode.__ireadPath = path(initialNode); initialNode.__ireadOffset = 0; textNodes.push(initialNode);
    }
    function endpoint(node, offset) {
        if (node && node.nodeType !== 3) {
            var candidate = node.childNodes[offset];
            if (candidate && candidate.nodeType === 3) { node = candidate; offset = 0; }
            else if (offset > 0 && node.childNodes[offset-1] && node.childNodes[offset-1].nodeType === 3) {
                node = node.childNodes[offset-1]; offset = node.length;
            }
        }
        return {path:path(node),offset:(node && node.__ireadOffset || 0)+offset};
    }
    function findPoint(value, end) {
        if (!value || !value.path || value.offset < 0) return null;
        var key = value.path.join('/'), found = null;
        for (var i=0;i<textNodes.length;i++) {
            var node=textNodes[i], base=node.__ireadOffset;
            if (node.parentNode && node.__ireadPath.join('/')===key && value.offset>=base && value.offset<=base+node.length) {
                found={node:node,offset:value.offset-base};
                if (end || value.offset<base+node.length) return found;
            }
        }
        return found;
    }
    function nodeAt(indices) {
        var node = body;
        for (var i = 0; indices && i < indices.length; i++) {
            node = node && node.childNodes[indices[i]];
        }
        return node;
    }
    function point(node, offset) {
        var r = document.createRange();
        if (!node) return null;
        try {
            r.setStart(node, Math.min(offset || 0, node.nodeType === 3 ? node.length : node.childNodes.length));
            r.setEnd(node, Math.min((offset || 0) + 1, node.nodeType === 3 ? node.length : node.childNodes.length));
            return r.getBoundingClientRect();
        } catch (e) { return null; }
    }
    function rangeFor(value) {
        var a=findPoint(value.start,false), b=findPoint(value.end,true), r=document.createRange();
        if (!a || !b) return null;
        try { r.setStart(a.node,a.offset); r.setEnd(b.node,b.offset); return r.collapsed ? null : r; } catch(e) { return null; }
    }
    function count() {
        if (fixed) return 1;
        return paged ? Math.max(1, Math.ceil((Math.max(body.scrollWidth, root.scrollWidth) - 1) / width)) :
            Math.max(1, Math.ceil(Math.max(body.scrollHeight, root.scrollHeight) / height));
    }
    function go(page) {
        if (fixed) return;
        page = Math.max(0, Math.min(count() - 1, page));
        window.scrollTo(paged ? page * width : 0, paged ? 0 : page * height);
    }
    var pageTextRects = null;
    function firstText() {
        if (paged && !fixed) {
            // Build a column index once. Walking every preceding text node and line rect
            // for each state() made late pages of illustrated chapters progressively slower.
            if (!pageTextRects) {
                pageTextRects = [];
                var indexedRange=document.createRange();
                for (var n=0;n<textNodes.length;n++) {
                    var textNode=textNodes[n];
                    if(!textNode.parentNode || !textNode.textContent.replace(/\s/g,''))continue;
                    indexedRange.selectNodeContents(textNode);
                    var lines=indexedRange.getClientRects();
                    for(var j=0;j<lines.length;j++) {
                        var line=lines[j], column=Math.floor((line.left+window.pageXOffset+1)/width);
                        if(column>=0 && !pageTextRects[column] && line.bottom>0 && line.top<height)
                            pageTextRects[column]={x:line.left+window.pageXOffset,y:line.top,node:textNode};
                    }
                }
            }
            var first=pageTextRects[Math.round(window.pageXOffset/width)];
            if(first) {
                var at=document.caretRangeFromPoint && document.caretRangeFromPoint(
                    Math.max(1,first.x-window.pageXOffset+1),Math.max(1,first.y+1));
                if(at && at.startContainer.nodeType===3)return endpoint(at.startContainer,at.startOffset);
                var low=0, high=first.node.length;
                while(low<high) {
                    var mid=Math.floor((low+high)/2), rect=point(first.node,mid);
                    var column=rect ? Math.floor((rect.left+window.pageXOffset+1)/width) : -1;
                    if(column<Math.round(window.pageXOffset/width))low=mid+1;else high=mid;
                }
                return endpoint(first.node,low);
            }
            return null;
        }
        var walker = document.createTreeWalker(body, 4, null, false), node, range = document.createRange();
        while ((node = walker.nextNode())) {
            if (!node.textContent.replace(/\s/g, '')) continue;
            range.selectNodeContents(node);
            var rects = range.getClientRects();
            for (var i = 0; i < rects.length; i++) {
                var rect = rects[i];
                if (rect.right > 0 && rect.left < width && rect.bottom > 0 && rect.top < height) {
                    var caret = document.caretRangeFromPoint && document.caretRangeFromPoint(
                        Math.max(1, rect.left + 1), Math.max(1, rect.top + 1));
                    return endpoint(caret ? caret.startContainer : node, caret ? caret.startOffset : 0);
                }
            }
        }
        return null;
    }
    function state() {
        var pages = count();
        var maxScroll = Math.max(0, Math.max(body.scrollHeight, root.scrollHeight) - height);
        // CSS column widths are fractional; Chromium quantizes scroll offsets and column
        // geometry independently. floor(offset/width) can report the previous column forever
        // (especially at the clamped last column). Paged movement always snaps to a column.
        var page = Math.max(0, Math.min(pages - 1, paged ? Math.round(window.pageXOffset / width) : Math.floor((window.pageYOffset + 1) / height)));
        if (!paged && window.pageYOffset >= maxScroll - 1) page = pages - 1;
        return {format:'iread-epub',version:1,href:config.href,page:page+1,pages:pages,
            progression:paged ? (pages > 1 ? page/(pages-1) : 0) : (maxScroll > 0 ? window.pageYOffset/maxScroll : 0), text:firstText()};
    }
    function locate(locator) {
        var rect = null;
        if (locator.text && locator.text.path) {
            var p=findPoint(locator.text,false);
            if (p) rect = point(p.node,p.offset);
            else rect = point(nodeAt(locator.text.path), locator.text.offset);
        }
        if (!rect && locator.fragment) {
            var element = document.getElementById(locator.fragment) || document.getElementsByName(locator.fragment)[0];
            if (element) rect = element.getBoundingClientRect();
        }
        if (rect) {
            if (paged) go(Math.floor((rect.left + window.pageXOffset + 1) / width));
            else window.scrollTo(0, rect.top + window.pageYOffset);
        } else if (paged) go(Math.round(Math.max(0, Math.min(1, locator.progression || 0)) * (count() - 1)));
        else window.scrollTo(0, Math.max(0, Math.min(1, locator.progression || 0)) * Math.max(0, Math.max(body.scrollHeight, root.scrollHeight) - height));
        return state();
    }
    var logicalSelection=null;
    function textRects(range) {
        var result=[], ancestor=range.commonAncestorContainer;
        function append(node) {
            if(!range.intersectsNode(node))return;
            var from=node===range.startContainer?range.startOffset:0, to=node===range.endContainer?range.endOffset:node.length;
            if(to<=from)return;
            var slice=document.createRange();slice.setStart(node,from);slice.setEnd(node,to);
            var rects=slice.getClientRects();
            for(var i=0;i<rects.length;i++)result.push(rects[i]);
        }
        if(ancestor.nodeType===3)append(ancestor);
        else {var walker=document.createTreeWalker(ancestor,4,null,false),node;while((node=walker.nextNode()))append(node);}
        return result;
    }
    function caretGeometry(value,end,visible) {
        var at=findPoint(value,end), rect=null;
        if (at) {
            var caret=document.createRange(); caret.setStart(at.node,at.offset); caret.collapse(true);
            rect=caret.getBoundingClientRect();
        }
        if (rect && rect.height>0 && (!paged || rect.left>=-1 && rect.left<=width+1 && rect.bottom>0 && rect.top<height))
            return {x:rect.left,top:rect.top,bottom:rect.bottom,proxy:false};
        if (!visible.length) return null;
        var v=visible[end?visible.length-1:0];
        return {x:end?v.right:v.left,top:v.top,bottom:v.bottom,proxy:true};
    }
    function selection() {
        if (!logicalSelection) return null;
        var r=rangeFor(logicalSelection);
        if (!r) return null;
        var rects = textRects(r), bounds = null, visible=[];
        for (var i=0;i<rects.length;i++) {
            var v=rects[i];
            if (v.right<=0 || v.left>=width || v.bottom<=0 || v.top>=height) continue;
            if (v.width<=0 || v.height<=0) continue;
            visible.push({left:Math.max(0,v.left),top:Math.max(0,v.top),right:Math.min(width,v.right),bottom:Math.min(height,v.bottom)});
            if (!bounds) bounds={left:Math.max(0,v.left),top:Math.max(0,v.top),right:Math.min(width,v.right),bottom:Math.min(height,v.bottom)};
            else { bounds.left=Math.min(bounds.left,Math.max(0,v.left)); bounds.top=Math.min(bounds.top,Math.max(0,v.top));
                bounds.right=Math.max(bounds.right,Math.min(width,v.right)); bounds.bottom=Math.max(bounds.bottom,Math.min(height,v.bottom)); }
        }
        return {text:r.toString(),domText:r.toString(),start:logicalSelection.start,
            end:logicalSelection.end,bounds:bounds,rects:paged?visible:rects.map(function(v){
                return {left:v.left,top:v.top,right:v.right,bottom:v.bottom};
            }),
            scrollX:window.pageXOffset,scrollY:window.pageYOffset,
            startCaret:caretGeometry(logicalSelection.start,false,visible),endCaret:caretGeometry(logicalSelection.end,true,visible)};
    }
    function select(value) {
        var range=rangeFor(value);
        if (!range || !range.toString().replace(/\s/g,'')) return null;
        logicalSelection={start:value.start,end:value.end};
        return selection();
    }
    function querySelect(value) {
        var previous=logicalSelection, result=select(value);
        logicalSelection=previous;
        return result;
    }
    function caretAt(x,y) {
        var caret=document.caretRangeFromPoint && document.caretRangeFromPoint(Math.max(1,Math.min(width-1,x)),Math.max(1,Math.min(height-1,y)));
        if (!caret || caret.startContainer.nodeType!==3) return null;
        return {point:endpoint(caret.startContainer,caret.startOffset),relativeOffset:caret.startOffset,text:caret.startContainer.textContent};
    }
    function paragraphAt(x,y) {
        var caret=caretAt(x,y);
        if (!caret) return null;
        var at=findPoint(caret.point,false), element=at && at.node.parentNode, paragraph=null;
        while(element && element!==body) {
            var mark=element.getAttribute && element.getAttribute('data-iread-mark');
            if (mark) for (var m=0;m<decorations.length;m++) if(decorations[m].id===mark) return querySelect(decorations[m].range);
            if (!paragraph && /^(P|LI|PRE|BLOCKQUOTE|H[1-6]|TD|TH|FIGCAPTION)$/i.test(element.nodeName)) paragraph=element;
            element=element.parentNode;
        }
        if (!paragraph) paragraph=at && at.node.parentNode;
        if (!paragraph || paragraph===body) return null;
        var walker=document.createTreeWalker(paragraph,4,null,false), n, first=null,last=null;
        while((n=walker.nextNode())) if(n.textContent.replace(/\s/g,'')) { if(!first)first=n;last=n; }
        if(!first || !last)return null;
        return querySelect({start:endpoint(first,0),end:endpoint(last,last.length)});
    }
    function extendSelection(value,start,moving) {
        return querySelect({start:start?moving:value.start,end:start?value.end:moving});
    }
    function pageForPoint(value) {
        var at=findPoint(value,false), rect=at && point(at.node,at.offset);
        if(!rect)return null;
        return Math.max(1,Math.min(count(),1+Math.floor(((paged?rect.left+window.pageXOffset:rect.top+window.pageYOffset)+1)/(paged?width:height))));
    }
    function scrollSelection(value,start,requested,y) {
        var fixed=findPoint(start?value.end:value.start,!start), rect=fixed && point(fixed.node,fixed.offset);
        if(!rect)return {moved:false};
        var distance=rect.top-y;
        if(start && requested>0)requested=Math.min(requested,Math.max(0,distance));
        if(!start && requested<0)requested=Math.max(requested,Math.min(0,distance));
        var before=window.pageYOffset;
        window.scrollTo(0,before+requested);
        return {moved:Math.abs(window.pageYOffset-before)>.1};
    }
    var decorations=[];
    function split(node, offset) {
        var tail=node.splitText(offset);
        tail.__ireadPath=node.__ireadPath; tail.__ireadOffset=node.__ireadOffset+offset;
        textNodes.push(tail); return tail;
    }
    function applyDecorations(items) {
        pageTextRects=null;
        var selected=selection(), scrollX=window.pageXOffset, scrollY=window.pageYOffset;
        var old=body.querySelectorAll('span[data-iread-mark]');
        for (var i=old.length-1;i>=0;i--) {
            var span=old[i];
            while(span.firstChild) span.parentNode.insertBefore(span.firstChild,span);
            span.parentNode.removeChild(span);
        }
        decorations=[];
        var statuses=[];
        for (var j=0;j<items.length;j++) {
            var item=items[j], r=rangeFor(item.range);
            if (!r || r.toString()!==(typeof item.range.domText==='string'?item.range.domText:item.quote)) { statuses.push({id:item.id,valid:false}); continue; }
            statuses.push({id:item.id,valid:true}); decorations.push(item);
            if (!item.color) continue;
            // Split each original text slice independently; no publication element is reparented.
            var nodes=textNodes.slice(0);
            for (var k=0;k<nodes.length;k++) {
                var n=nodes[k];
                if (!n.parentNode || !r.intersectsNode(n)) continue;
                var from=n===r.startContainer?r.startOffset:0, to=n===r.endContainer?r.endOffset:n.length;
                if (to<=from) continue;
                if (to<n.length) split(n,to);
                if (from>0) n=split(n,from);
                var wrapper=document.createElement('span'); wrapper.setAttribute('data-iread-mark',item.id);
                wrapper.style.cssText='font:inherit!important;color:inherit!important;line-height:inherit!important;letter-spacing:inherit!important;';
                wrapper.style.setProperty('background-color',item.color,'important');
                n.parentNode.insertBefore(wrapper,n); wrapper.appendChild(n);
                // Recreate the range because splitText may have changed its live boundary nodes.
                r=rangeFor(item.range);
            }
        }
        if (selected) logicalSelection={start:selected.start,end:selected.end};
        window.scrollTo(scrollX,scrollY);
        return {statuses:statuses,geometry:decorationGeometry()};
    }
    function decorationGeometry() {
        var output=[];
        for (var i=0;i<decorations.length;i++) {
            var item=decorations[i], r=rangeFor(item.range);
            if (!r || !item.underline) continue;
            var rects=textRects(r), lines=[];
            for (var j=0;j<rects.length;j++) {
                var v=rects[j];
                if (v.right>0 && v.left<width && v.bottom>0 && v.top<height)
                    lines.push({left:Math.max(0,v.left),right:Math.min(width,v.right),bottom:v.bottom});
            }
            output.push({id:item.id,lines:lines});
        }
        return output;
    }
    function hit(x,y) {
        var e=document.elementFromPoint(x,y);
        while (e && e!==body) {
            var href=e.getAttribute && (e.getAttribute('href') || e.getAttribute('xlink:href'));
            if (href) return {href:href};
            e=e.parentNode;
        }
        return {};
    }
    function links() {
        var nodes=body.querySelectorAll('a[href]'), result=[];
        for(var i=0;i<nodes.length;i++) {
            var rects=nodes[i].getClientRects();
            for(var j=0;j<rects.length;j++) {
                var r=rects[j];
                if(r.right>0 && r.left<width && r.bottom>0 && r.top<height)
                    result.push({href:nodes[i].getAttribute('href'),left:r.left,top:r.top,right:r.right,bottom:r.bottom});
            }
        }
        return result;
    }
    function marks() {
        var result=[];
        for(var i=0;i<decorations.length;i++) {
            var item=decorations[i], r=item.color && rangeFor(item.range);
            if(!r)continue;
            var rects=textRects(r);
            for(var j=0;j<rects.length;j++) {
                var v=rects[j];
                if(v.right>0 && v.left<width && v.bottom>0 && v.top<height)
                    result.push({id:item.id,left:v.left,top:v.top,right:v.right,bottom:v.bottom});
            }
        }
        return result;
    }
    window.__iread={state:state,locate:locate,goToPage:function(page){go(page-1);return state();},
        selection:selection,hit:hit,applyDecorations:applyDecorations,decorationGeometry:decorationGeometry,
        paragraphAt:paragraphAt,caretAt:caretAt,extendSelection:extendSelection,select:select,pageForPoint:pageForPoint,links:links,marks:marks,scrollSelection:scrollSelection,
        clearSelection:function(){logicalSelection=null;}};
    if (typeof config.initialPage === 'number') { go(config.initialPage - 1); return state(); }
    return locate(config.locator);
})
