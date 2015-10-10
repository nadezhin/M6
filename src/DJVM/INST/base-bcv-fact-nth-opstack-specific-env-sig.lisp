(in-package "BCV")
(include-book "../../BCV/typechecker")
(include-book "../../BCV/bcv-functions")

(include-book "base-bind-free")

(local 
 (defthm nth-i-cdr
   (implies (and (syntaxp (and (equal (car i) 'QUOTE)
                               (integerp (cdr i))))
                 (integerp i)
                 (< 0 i))
         (equal (nth i stk)
                (nth (- i 1) (cdr stk))))))


;; (i-am-here)

(local 
 (defthm nth-operand-stack-normalize
  (implies (and (bind-free (acl2::default-bind-free 's 's (acl2::pkg-witness "DJVM"))
                           (s))
                (isMatchingType type stk env)
                (equal (sizeof type) 1)
                (not (equal type 'void))
                (integerp i)
                (< 0 i))
         (equal (nth i stk)
                (nth (- i 1) (popMatchingType type stk))))
  :hints (("Goal" :in-theory (e/d (popMatchingType)
                                  (isMatchingType))))))

;;; export the specific version of the the generalized version to force ACL2 to
;;; try different possibililties instead of complaining about Free variable
;;; "type"

;;;
;;; notice this is almost a pattern. 
;;;

(defthm nth-operand-stack-normalize-INT-env-sig
  (implies (and (bind-free (acl2::default-bind-free 's 's (acl2::pkg-witness "DJVM"))
                           (s))
                (isMatchingType 'INT stk env)
                (integerp i)
                (< 0 i))
         (equal (nth i stk)
                (nth (- i 1) (popMatchingType 'INT stk))))
  :hints (("Goal" :use ((:instance nth-operand-stack-normalize
                                   (env env)
                                   (type 'INT))))))


(defthm nth-operand-stack-normalize-AARARRY-env-sig
  (implies (and (bind-free (acl2::default-bind-free 's 's (acl2::pkg-witness "DJVM"))
                           (s))
                (isMatchingType '(array (class "java.lang.Object")) stk env)
                (integerp i)
                (< 0 i))
         (equal (nth i stk)
                (nth (- i 1) (popMatchingType '(array (class "java.lang.Object")) stk))))
  :hints (("Goal" :use ((:instance nth-operand-stack-normalize
                                   (env env)
                                   (type '(array (class "java.lang.Object"))))))))





