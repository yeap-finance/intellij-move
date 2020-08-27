package org.move.ide.formatter

import org.move.utils.tests.MoveTypingTestCase

class AutoIndentTest : MoveTypingTestCase() {
    fun `test script`() = doTestByText(
        """
        script {/*caret*/}
    """, """
        script {
            /*caret*/
        }
    """
    )

    fun `test module`() = doTestByText(
        """
        module M {/*caret*/}
    """, """
        module M {
            /*caret*/
        }
    """
    )

    fun `test address`() = doTestByText(
        """
        address 0x0 {/*caret*/}
    """, """
        address 0x0 {
            /*caret*/
        }
    """
    )

    fun `test function`() = doTestByText(
        """
        script {
            fun main() {/*caret*/}
        }
    """, """
        script {
            fun main() {
                /*caret*/
            }
        }
    """
    )

    fun `test second function in module`() = doTestByText(
        """
       module M {
           fun main() {}/*caret*/ 
       }
    """, """
       module M {
           fun main() {}
           /*caret*/
       }
    """
    )

    fun `test struct`() = doTestByText(
        """
       module M {
           struct MyStruct {/*caret*/}
       } 
    """, """
       module M {
           struct MyStruct {
               /*caret*/
           }
       } 
    """
    )

    fun `test resource struct`() = doTestByText(
        """
       module M {
           resource struct MyStruct {/*caret*/}
       } 
    """, """
       module M {
           resource struct MyStruct {
               /*caret*/
           }
       } 
    """
    )

    fun `test function params`() = doTestByText(
        """
       script {
           fun main(a: u8, /*caret*/b: u8) {}
       } 
    """, """
       script {
           fun main(a: u8, 
                    /*caret*/b: u8) {}
       } 
    """
    )
}