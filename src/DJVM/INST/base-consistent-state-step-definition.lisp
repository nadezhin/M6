(in-package "DJVM")
(include-book "../../DJVM/consistent-state")
(include-book "../../DJVM/consistent-state-properties")
(include-book "../../M6-DJVM-shared/jvm-loader-guard-verification")

(defun consistent-state-step (s)
  (and
   (wff-state s)
   (wff-env (env s))
   (wff-aux (aux s))
   (alistp (heap-init-map (aux s)))
   (wff-heap-init-map-strong (heap-init-map (aux s)))
   (wff-class-table (class-table s))
   (wff-instance-class-table-strong (instance-class-table s))
   (wff-array-class-table (array-class-table s))
   (wff-static-class-table-strong (external-class-table s))
   (wff-methods-instance-class-table-strong
    (instance-class-table s))
   (consistent-class-hierachy (instance-class-table s))
   (consistent-heap (heap s)
                    (instance-class-table s)
                    (array-class-table s))
   (consistent-heap-init-state (heap s)
                               (instance-class-table s)
                               (heap-init-map (aux s)))
   (consistent-heap-array-init-state (heap s)
                                     (instance-class-table s)
                                     (array-class-table s)
                                     (heap-init-map (aux s)))
   (consistent-class-table (instance-class-table s)
                           (external-class-table s)
                           (heap s))
   (consistent-thread-table (thread-table s)
                            (instance-class-table s)
                            (heap s))
   (unique-id-thread-table (thread-table s))
   (thread-exists? (current-thread s)
                   (thread-table s))
   (instance-class-table-inv s)
   (array-class-table-inv s)
   (boot-strap-class-okp s)
   (equal (bcv::scl-bcv2jvm
           (bcv::scl-jvm2bcv (external-class-table s)))
          (external-class-table s)) 
   (bcv::good-scl (bcv::scl-jvm2bcv (external-class-table s)))))

;; Fri Jul 15 21:05:31 2005

