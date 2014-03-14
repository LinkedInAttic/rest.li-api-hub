!function ($) {

  $(function(){
    var method = $('#http-method-select').val();
    if(method=='PUT' || method=='POST') {
      $('#request-body-section').show();
    } else {
      $('#request-body-section').hide();
    }
  });
  
  $('#http-method-select').change(function(){
    var method = $(this).val();
    if(method=='PUT' || method=='POST') {
      $('#request-body-section').show();
    } else {
      $('#request-body-section').hide();
    }
  });

  $('#permlink-btn').click(function(){
    var paste_data = $('#console-form').serializeArray();
    paste_data.push({
      name: "origin",
      value: window.location
    });

    $.ajax({
      url: $(this).data('url'),
      type: 'POST',
      data: $.param(paste_data)
    }).done(function (data) {
      $('#permlink').val(data).focus().select();
    });
  });

}(window.jQuery);