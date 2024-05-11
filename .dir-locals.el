((clojure-mode
  (eval .
        (defun custom-eval-user-go ()
          (interactive)
          (cider-interactive-eval
           (format "(do (in-ns 'menu-fillova.core) (go))"
                   (cider-last-sexp)))))

  (eval .
        (define-key cider-mode-map (kbd "C-c g") 'custom-eval-user-go))

  ))

