!function ($) {

  var c = 0;
  var colors = ["#113F8C", "#01A4A4", "#00A1CB", "#61AE24", "#D0D102", "#32742C", "#D70060", "#E54028", "#F18D05", "#616161"];


  $(".doughnut-chart").each(function() {
    var ctx = $(".chart-canvas", this).get(0).getContext("2d")
    var data = [];
    $(".data-points li", this).each(function(i) {
      $(".data-point-legend", this).css("color", colors[c]);
      data.push({
        value: $(this).data("value"),
        color: colors[c]
      });
      c = (c + 1) % colors.length;
    });

    var chart = new Chart(ctx).Doughnut(data, { animation: false });
  });

  $(".line-chart").each(function() {
    var ctx = $(".chart-canvas", this).get(0).getContext("2d")
    var labels = [];
    var values = [];
    $(".data-points li", this).each(function(i) {
      labels.push($(this).data("name"));
      values.push($(this).data("value"));
    });

    var data = {
      labels: labels,
      datasets: [
        {
          fillColor : "rgba(220,220,220,0.5)",
          strokeColor : "rgba(220,220,220,1)",
          pointColor : "rgba(220,220,220,1)",
          pointStrokeColor : "#fff",
          data: values
        }
      ]
    };
    console.log(data)
    var chart = new Chart(ctx).Line(data, {
      animation: false,
      scaleShowGridLines: false
    });
  });

  $(".radar-chart").each(function() {
    var ctx = $(".chart-canvas", this).get(0).getContext("2d")
    var labels = [];
    var values = [];
    $(".data-points li", this).each(function(i) {
      labels.push($(this).data("name"));
      values.push($(this).data("value"));
    });

    var data = {
      labels: labels,
      datasets: [
        {
          fillColor : "rgba(220,220,220,0.5)",
          strokeColor : "rgba(220,220,220,1)",
          pointColor : "rgba(220,220,220,1)",
          pointStrokeColor : "#fff",
          data: values
        }
      ]
    };
    console.log(data)
    var chart = new Chart(ctx).Radar(data, {
      animation: false,
      scaleShowGridLines: false
    });
  });

  $(".bar-chart").each(function() {
    var ctx = $(".chart-canvas", this).get(0).getContext("2d")
    var labels = [];
    var values = [];
    $(".data-points li", this).each(function(i) {
      labels.push($(this).data("name"));
      values.push($(this).data("value"));
      c = (c + 1) % colors.length;
    });

    var data = {
      labels: labels,
      datasets: [
        {
          fillColor : "rgba(220,220,220,0.5)",
          strokeColor : "rgba(220,220,220,1)",
          data: values
        }
      ]
    };
    console.log(data)
    var chart = new Chart(ctx).Bar(data, {
      animation: false,
      scaleShowGridLines: false
    });
  });

}(window.jQuery);