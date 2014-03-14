!function ($) {

  $(function(){
    $("a[rel=popover]")
          .popover()
          .click(function(e) {
            e.preventDefault()
          })
  })
}(window.jQuery);