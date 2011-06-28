(eval-when-compile (require 'cl))
(require 'json)

;; Required global variables: tinkernotes-rexster-host, tinkernotes-rexster-port, tinkernotes-rexster-graph
;;
;; For example:
;;
;;     (defun tinkernotes ()
;;         (defvar tinkernotes-rexster-host "localhost")
;;         (defvar tinkernotes-rexster-port "8182")
;;         (defvar tinkernotes-rexster-graph "tinkernotes"))


;; HELPER CODE ;;;;;;;;;;;;;;;;;;;;;;;;;

;; from Emacs-w3m
(defun w3m-url-encode-string (str &optional coding)
  (apply (function concat)
         (mapcar
          (lambda (ch)
            (cond
             ((string-match "[-a-zA-Z0-9_:/]" (char-to-string ch)) ; xxx?
              (char-to-string ch))      ; printable
             (t
              (format "%%%02X" ch))))   ; escape
          ;; Coerce a string to a list of chars.
          (append (encode-coding-string str (or coding 'iso-2022-jp))
                  nil))))

(defun http-post (url args callback)
  "Send ARGS to URL as a POST request."
  (let ((url-request-method "POST")
        (url-request-extra-headers
         '(("Content-Type" . "application/x-www-form-urlencoded")))
        (url-request-data
         (mapconcat (lambda (arg)
                      (concat (w3m-url-encode-string (car arg))
                              "="
                              (w3m-url-encode-string (car (last arg)))))
;;                      (concat (url-hexify-string (car arg))
;;                              "="
;;                              (url-hexify-string (cdr arg))))
                    args
                    "&")))
    (url-retrieve url callback)))

(defun strip-http-headers (entity)
    (let ((i (string-match "\n\n" entity)))
        (if (>= i 0)
            (substring entity (+ i 2))
            entity)))


;; BUFFERS ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defun current-line ()
    (interactive)
    (buffer-substring-no-properties (line-beginning-position) (line-end-position)))

;; Buffer-local variables. Given them initial, global bindings so they're defined before there are actual view buffers.
(setq view-depth 3)
(setq view-root nil)
(setq view-title nil)
(setq view-inverse nil)
(setq view-min-sharability 0)
(setq view-max-sharability 1)
(setq view-min-weight 0)
(setq view-max-weight 1)

(defun find-id ()
    (let ((line (current-line)))
        (if (string-match "^\([0-9A-Za-z+/]*:[0-9A-Za-z+/]*\)" line)
            (let (
                (i1 (string-match "\(" line))
                (i2 (string-match ":" line))
                (i3 (string-match "\)" line)))
                (let (
                    (s1 (substring line (+ 1 i1) i2))
                    (s2 (substring line (+ 1 i2) i3)))
                    (let (
                        (assoc-id (if (< 0 (length s1)) s1 nil))
                        (atom-id (if (< 0 (length s2)) s2 nil)))
                        (list assoc-id atom-id))))
            (list nil nil))))


(defun view-name (root-id)
    (if root-id
        (concat "view-" root-id)
        "anonymous view"))


;; COMMUNICATION ;;;;;;;;;;;;;;;;;;;;;;;

(defun base-url ()
    (concat "http://" tinkernotes-rexster-host ":" tinkernotes-rexster-port "/" tinkernotes-rexster-graph "/tinkernotes/"))

(defun receive-view (status)
    (let ((json (json-read-from-string (strip-http-headers (buffer-string)))))
        (if status
            (let ((msg (gethash "message" json))
                (error (gethash "error" json)))
                    (if error
                        (error-message error)
                        (error-message msg)))

            (let (
                (root (cdr (assoc 'root json)))
                (view (cdr (assoc 'view json)))
                (depth (cdr (assoc 'depth json)))
                (min-sharability (string-to-number (cdr (assoc 'minSharability json))))
                (max-sharability (string-to-number (cdr (assoc 'maxSharability json))))
                (min-weight (string-to-number (cdr (assoc 'minWeight json))))
                (max-weight (string-to-number (cdr (assoc 'maxWeight json))))
                (inverse (string-equal "true" (cdr (assoc 'inverse json))))
                (title (cdr (assoc 'title json))))
                    (switch-to-buffer (view-name root))
                    ;;(tinkernotes-mode)
                    (erase-buffer)
                    (let ((view-json (json-read-from-string view)))
                        (write-view (cdr (assoc 'children view-json)) 0))
                    (beginning-of-buffer)
                    (make-local-variable 'view-root)
                    (make-local-variable 'view-depth)
                    (make-local-variable 'view-inverse)
                    (make-local-variable 'view-title)
                    (make-local-variable 'view-min-sharability)
                    (make-local-variable 'view-max-sharability)
                    (make-local-variable 'view-min-weight)
                    (make-local-variable 'view-max-weight)
                    (setq view-root root)
                    (if depth (setq view-depth (string-to-number depth)))
                    (setq view-min-sharability min-sharability)
                    (setq view-max-sharability max-sharability)
                    (setq view-min-weight min-weight)
                    (setq view-max-weight max-weight)
                    (setq view-inverse inverse)
                    (setq view-title title)
                    (info-message (concat "updated to view " (view-info)))))))

(setq full-colors '(
    "#330000" "#660000" "#990000" "#CC0000"  ;; private:   red
    "#332600" "#664C00" "#997200" "#CC9900"  ;; protected: orange
    "#003300" "#006600" "#009900" "#00CC00"  ;; public:    green
    "#000066" "#000099" "#0000CC" "#0000FF"  ;; demo:      blue
    ))

(setq reduced-colors '("red" "red" "blue" "blue"))

(setq full-colors-supported (> (length (defined-colors)) 8))

(defun colorize (text weight sharability bold)
    (let (
        (i (- (ceiling (* sharability 4)) 1))
        (j (- (ceiling (* weight 4)) 1)))
            (let ((color
                (if full-colors-supported
                    (elt full-colors (+ j (* i 4)))
                    (elt reduced-colors i))))
	    (if bold
        (propertize text 'face (list 'bold 'italic  :foreground color))
        (propertize text 'face (list :foreground color))))))

(defun write-view (children indent)
    (loop for json across children do
    (let (
        (link (cdr (assoc 'link json)))
        (target (cdr (assoc 'target json)))
        (children (cdr (assoc 'children json))))
            (let (
                (link-key (cdr (assoc 'key link)))
                (link-value (cdr (assoc 'value link)))
		        (link-weight (cdr (assoc 'weight link)))
	            (link-sharability (cdr (assoc 'sharability link)))
                (target-key (cdr (assoc 'key target)))
                (target-value (cdr (assoc 'value target)))
		        (target-weight (cdr (assoc 'weight target)))
		        (target-sharability (cdr (assoc 'sharability target))))
		            ;;(if (not link-key) (error "missing link key"))
		            ;;(if (not link-value) (error (concat "missing value for link with key " link-key)))
		            (if (not link-weight) (error (concat "missing weight for link with key " link-key)))
		            (if (not link-sharability) (error (concat "missing sharability for link with key " link-key)))
		            (if (not target-key) (error "missing target key"))
		            (if (not target-value) (error (concat "missing value for target with key " target-key)))
		            (if (not target-weight) (error (concat "missing weight for target with key " target-key)))
		            (if (not target-sharability) (error (concat "missing sharability for target with key " target-key)))
                    (insert
		                (propertize (concat "(" link-key ":" target-key ") ")
					'face '(:foreground "white")
					'invisible t
		                    'target-weight target-weight
		                	'target-sharability target-sharability
		                	'link-weight link-weight
			                'link-sharability link-sharability))
			        (loop for i from 1 to indent do (insert "    "))
                    (insert (concat
		                (colorize link-value link-weight link-sharability t) "  "
			            (colorize target-value target-weight target-sharability nil) "\n"))
                    (write-view children (+ indent 1))
                    ))))


;; VIEWS ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defun view-info ()
    (concat
        "(root: " view-root
         " :depth " (number-to-string view-depth)
         " :inverse " (if view-inverse "t" "nil")
         " :sharability [" (number-to-string view-min-sharability) ", " (number-to-string view-max-sharability) "]"
         " :weight [" (number-to-string view-min-weight) ", " (number-to-string view-max-weight) "]"
         " :title \"" view-title "\")"))  ;; TODO: actuallly escape the title string

(defun request-view (root depth inverse minv maxv minw maxw)
    (url-retrieve
        (concat (base-url) "view"
            "?root=" (w3m-url-encode-string root)
            "&depth=" (number-to-string depth)
            "&minSharability=" (number-to-string minv)
            "&maxSharability=" (number-to-string maxv)
            "&minWeight=" (number-to-string minw)
            "&maxWeight=" (number-to-string maxw)
            "&inverse=" (if inverse "true" "false")) 'receive-view))

(defun request-view-url  (root depth inverse minv maxv minw maxw)
	(concat (base-url) "view"
            "?root=" (w3m-url-encode-string root)
            "&depth=" (number-to-string depth)
            "&minSharability=" (number-to-string minv)
            "&maxSharability=" (number-to-string maxv)
            "&minWeight=" (number-to-string minw)
            "&maxWeight=" (number-to-string maxw)
            "&inverse=" (if inverse "true" "false")))

(defun refresh-view-new (url)
    (url-retrieve url 'receive-view))

(defun request-search-results (query minv maxv minw maxw)
    (url-retrieve
        (concat (base-url) "search"
            "?query=" (w3m-url-encode-string query)
            "&depth=2"
            "&inverse=false"
            "&minSharability=" (number-to-string minv)
            "&maxSharability=" (number-to-string maxv)
            "&minWeight=" (number-to-string minw)
            "&maxWeight=" (number-to-string maxw)) 'receive-view))

(defun find-link-weight ()
    (get-text-property (line-beginning-position) 'link-weight))

(defun find-link-sharability ()
    (get-text-property (line-beginning-position) 'link-sharability))

(defun find-target-weight ()
    (get-text-property (line-beginning-position) 'target-weight))

(defun find-target-sharability ()
    (get-text-property (line-beginning-position) 'target-sharability))

(defun set-properties (key weight sharability)
    (interactive)
    (if view-root
        (let ((url (request-view-url view-root view-depth view-inverse view-min-sharability view-max-sharability view-min-weight view-max-weight)))
(setq hack-url url)
            (url-retrieve
                (concat (base-url) "set"
                    "?key=" (w3m-url-encode-string key)
                    "&weight=" (number-to-string weight)
                    "&sharability=" (number-to-string sharability))
	(lambda (status)
        (let ((json (json-read-from-string (strip-http-headers (buffer-string)))))
            (if status
                (let ((msg (cdr (assoc 'message json)))
				    (error (cdr (assoc 'error json))))
                        (if error
                            (error-message error)
                            (error-message msg)))
		         (refresh-view-new hack-url))))
		                     ))
        (not-in-view)))

(defun current-line-target ()
    (car (last (find-id))))

(defun current-line-link ()
    (car (find-id)))

(defun visit-target ()
    (interactive)
    (let ((key (current-line-target)))
        (if key
            (request-view key view-depth view-inverse view-min-sharability view-max-sharability view-min-weight view-max-weight))))

(defun visit-link ()
    (interactive)
    (let ((key (current-line-link)))
        (if key
            (request-view key view-depth view-inverse view-min-sharability view-max-sharability view-min-weight view-max-weight))))

(defun decrease-link-weight ()
    (interactive)
    (let (
        (key (current-line-link))
	(weight (find-link-weight))
	(sharability (find-link-sharability)))
            (if key
	        (set-properties key (- weight 0.25) sharability))))

(defun increase-link-weight ()
    (interactive)
    (let (
        (key (current-line-link))
	(weight (find-link-weight))
	(sharability (find-link-sharability)))
            (if key
	        (set-properties key (+ weight 0.25) sharability))))

(defun decrease-target-weight ()
    (interactive)
    (let (
        (key (current-line-target))
	(weight (find-target-weight))
	(sharability (find-target-sharability)))
            (if key
	        (set-properties key (- weight 0.25) sharability))))

(defun increase-target-weight ()
    (interactive)
    (let (
        (key (current-line-target))
	(weight (find-target-weight))
	(sharability (find-target-sharability)))
            (if key
	        (set-properties key (+ weight 0.25) sharability))))

(defun decrease-link-sharability ()
    (interactive)
    (let (
        (key (current-line-link))
	(weight (find-link-weight))
	(sharability (find-link-sharability)))
            (if key
		    (set-properties key weight (- sharability 0.25)))))

(defun increase-link-sharability ()
    (interactive)
    (let (
        (key (current-line-link))
	(weight (find-link-weight))
	(sharability (find-link-sharability)))
            (if key
		    (set-properties key weight (+ sharability 0.25)))))

(defun decrease-target-sharability ()
    (interactive)
    (let (
        (key (current-line-target))
	(weight (find-target-weight))
	(sharability (find-target-sharability)))
            (if key
		    (set-properties key weight (- sharability 0.25)))))

(defun increase-target-sharability ()
    (interactive)
    (let (
        (key (current-line-target))
	(weight (find-target-weight))
	(sharability (find-target-sharability)))
            (if key
		    (set-properties key weight (+ sharability 0.25)))))

(defun search ()
    (interactive)
    (let ((query (read-from-minibuffer "query: ")))
        (if (> (length query) 0)
            (request-search-results
                (concat "*" query "*")
                view-min-sharability view-max-sharability view-min-weight view-max-weight))))

(defun not-in-view ()
	(error-message "this command must be executed from within a view"))

(defun refresh-view ()
    (interactive)
    (if view-root
        (request-view view-root view-depth view-inverse view-min-sharability view-max-sharability view-min-weight view-max-weight))
        (not-in-view))

(defun decrease-depth ()
    (interactive)
    (if view-root
        (request-view view-root (- view-depth 1) view-inverse view-min-sharability view-max-sharability view-min-weight view-max-weight))
        (not-in-view))

(defun increase-depth ()
    (interactive)
    (if view-root
        (request-view view-root (+ view-depth 1) view-inverse view-min-sharability view-max-sharability view-min-weight view-max-weight))
        (not-in-view))

(defun invert-view ()
    (interactive)
    (if view-root
        (request-view view-root view-depth (not view-inverse) view-min-sharability view-max-sharability view-min-weight view-max-weight))
        (not-in-view))

(defun decrease-min-weight ()
    (interactive)
    (if view-root
        (request-view view-root view-depth view-inverse view-min-sharability view-max-sharability (- view-min-weight 0.25) view-max-weight))
        (not-in-view))

(defun increase-min-weight ()
    (interactive)
    (if view-root
        (request-view view-root view-depth view-inverse view-min-sharability view-max-sharability (+ view-min-weight 0.25) view-max-weight))
        (not-in-view))

(defun decrease-max-weight ()
    (interactive)
    (if view-root
        (request-view view-root view-depth view-inverse view-min-sharability view-max-sharability view-min-weight (- view-max-weight 0.25)))
        (not-in-view))

(defun increase-max-weight ()
    (interactive)
    (if view-root
        (request-view view-root view-depth view-inverse view-min-sharability view-max-sharability view-min-weight (+ view-max-weight 0.25)))
        (not-in-view))

(defun decrease-min-sharability ()
    (interactive)
    (if view-root
        (request-view view-root view-depth view-inverse (- view-min-sharability 0.25) view-max-sharability view-min-weight view-max-weight))
            (not-in-view))

(defun increase-min-sharability ()
    (interactive)
    (if view-root
        (request-view view-root view-depth view-inverse (+ view-min-sharability 0.25) view-max-sharability view-min-weight view-max-weight))
            (not-in-view))

(defun decrease-max-sharability ()
    (interactive)
    (if view-root
        (request-view view-root view-depth view-inverse view-min-sharability (- view-max-sharability 0.25) view-min-weight view-max-weight))
            (not-in-view))

(defun increase-max-sharability ()
    (interactive)
    (if view-root
        (request-view view-root view-depth view-inverse view-min-sharability (+ view-max-sharability 0.25) view-min-weight view-max-weight))
            (not-in-view))


;; UPDATES ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defun push-view ()
    (interactive)
    (let (
        (entity (buffer-string)))
        (http-post
            (concat (base-url) "update")
            (list
                (list "root" view-root)
                (list "view" entity)
                (list "inverse" (if view-inverse "true" "false"))
                (list "minSharability" (number-to-string view-min-sharability))
                (list "maxSharability" (number-to-string view-max-sharability))
                (list "minWeight" (number-to-string view-min-weight))
                (list "maxWeight" (number-to-string view-max-weight))
                (list "depth" (number-to-string view-depth)))
            'receive-view)))


;; INTERFACE ;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defun info-message (msg)
    (message (concat "Info: " msg)))

(defun error-message (msg)
    (message (concat "Error: " msg)))

(defun my-debug ()
    (interactive)
    (message (number-to-string (length (defined-colors)))))


(global-set-key (kbd "C-c t") 'visit-target)
(global-set-key (kbd "C-c l") 'visit-link)
(global-set-key (kbd "C-c r") 'refresh-view)
(global-set-key (kbd "C-c C-d ,") 'decrease-depth)
(global-set-key (kbd "C-c C-d .") 'increase-depth)
(global-set-key (kbd "C-c ~") 'invert-view)
(global-set-key (kbd "C-c p") 'push-view)
(global-set-key (kbd "C-c d") 'my-debug)
(global-set-key (kbd "C-c s") 'search)
(global-set-key (kbd "C-c C-w C-[ ,") 'decrease-min-weight)
(global-set-key (kbd "C-c C-w C-[ .") 'increase-min-weight)
(global-set-key (kbd "C-c C-w C-] ,") 'decrease-max-weight)
(global-set-key (kbd "C-c C-w C-] .") 'increase-max-weight)
(global-set-key (kbd "C-c C-v C-[ ,") 'decrease-min-sharability)
(global-set-key (kbd "C-c C-v C-[ .") 'increase-min-sharability)
(global-set-key (kbd "C-c C-v C-] ,") 'decrease-max-sharability)
(global-set-key (kbd "C-c C-v C-] .") 'increase-max-sharability)
(global-set-key (kbd "C-c C-l C-w ,") 'decrease-link-weight)
(global-set-key (kbd "C-c C-l C-w .") 'increase-link-weight)
(global-set-key (kbd "C-c C-t C-w ,") 'decrease-target-weight)
(global-set-key (kbd "C-c C-t C-w .") 'increase-target-weight)
(global-set-key (kbd "C-c C-l C-s ,") 'decrease-link-sharability)
(global-set-key (kbd "C-c C-l C-s .") 'increase-link-sharability)
(global-set-key (kbd "C-c C-t C-s ,") 'decrease-target-sharability)
(global-set-key (kbd "C-c C-t C-s .") 'increase-target-sharability)

;; Note: these should perhaps be local settings
(global-set-key (kbd "C-c C-s C-t") 'toggle-truncate-lines)
(setq-default truncate-lines t)
(global-hl-line-mode 1)
(set-face-background 'hl-line "ivory")


;;(setq syntax-keywords
;; '(
;;   ("^\([0-9A-Za-z+/]*:[0-9A-Za-z+/]*\)" . font-lock-doc-face)
;;  ))
;;
;;(define-derived-mode tinkernotes-mode fundamental-mode
;;  (setq font-lock-defaults '(syntax-keywords))
;;  (setq mode-name "tinkernotes")
;;)


;; Uncomment only when debugging
(add-hook 'after-init-hook '(lambda () (setq debug-on-error t)))


(provide 'tinkernotes)
