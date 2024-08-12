((src .
      ((eval .
             (progn
               (defun custom-eval-user-go ()
                 (interactive)
                 (cider-interactive-eval
                  (format "(do (in-ns 'menu-fillova.core) (go))"
                          (cider-last-sexp))))

               (define-key cider-mode-map (kbd "C-c g") 'custom-eval-user-go)
             )

             ))


  ))

