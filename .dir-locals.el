((nil .

       ((eval .
              (progn

                (message "running .dir-locals.el 12345")
                (setq cider-clojure-cli-global-options "-A:dev:test")
              
                (defun custom-eval-user-go ()
                  (interactive)
                  (cider-interactive-eval
                   (format "(do (in-ns 'user) (go))"
                           (cider-last-sexp))))

                (define-key cider-mode-map (kbd "C-c g") 'custom-eval-user-go)
              )

              ))


  ))
