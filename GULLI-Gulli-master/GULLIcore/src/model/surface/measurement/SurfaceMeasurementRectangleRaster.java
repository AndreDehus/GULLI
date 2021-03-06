/*
 * The MIT License
 *
 * Copyright 2018 saemann.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package model.surface.measurement;

import com.vividsolutions.jts.geom.Coordinate;
import model.particle.Particle;
import model.surface.Surface;
import model.timeline.array.TimeIndexContainer;

/**
 * Raster represneting surface in rectangles for very fast index finding
 *
 * @author saemann
 */
public class SurfaceMeasurementRectangleRaster extends SurfaceMeasurementRaster {

    /**
     * [x-index][y-index][timeindex][materialindex]:mass
     */
    double[][][][] mass;

    /**
     * [x-index][y-index][timeindex][materialindex]:counter
     */
    int[][][][] particlecounter;

    int numberXIntervals, numberYIntervals, numberOfMaterials;
    double xIntervalWidth, YIntervalHeight;
    double xmin, ymin;

    public SurfaceMeasurementRectangleRaster(double xmin, double ymin, int numberXIntervals, int numberYIntervals, double xIntervalWidth, double yIntervalHeight, int numberMaterials, TimeIndexContainer time) {
        this.times = time;
        this.numberOfMaterials = numberMaterials;
        this.xIntervalWidth = xIntervalWidth;
        this.YIntervalHeight = yIntervalHeight;
        this.xmin = xmin;
        this.ymin = ymin;
        this.numberXIntervals = numberXIntervals;
        this.numberYIntervals = numberYIntervals;

        mass = new double[numberXIntervals][][][];
        particlecounter = new int[numberXIntervals][][][];
    }

    public static SurfaceMeasurementRectangleRaster SurfaceMeasurementRectangleRaster(Surface surf, int numberXInterval, int numberYInterval) {
        double minX = Double.POSITIVE_INFINITY, maxX = Double.NEGATIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY;

        for (double[] vertex : surf.getVerticesPosition()) {
            minX = Math.min(minX, vertex[0]);
            maxX = Math.max(maxX, vertex[0]);
            minY = Math.min(minY, vertex[1]);
            maxY = Math.max(maxY, vertex[1]);
        }

        double xwidth = (maxX - minX) / (double) numberXInterval;
        double ywidth = (maxY - minY) / (double) numberYInterval;
        return new SurfaceMeasurementRectangleRaster(minX, minY, numberXInterval, numberYInterval, xwidth, ywidth, surf.getNumberOfMaterials(), surf.getTimes());
    }

    public static SurfaceMeasurementRaster SurfaceMeasurementRectangleRaster(Surface surface, int numberXIntervals, int numberYIntervals, int numberMaterials) {

        SurfaceMeasurementRectangleRaster smr = SurfaceMeasurementRectangleRaster(surface, numberXIntervals, numberYIntervals);
        smr.setNumberOfMaterials(numberMaterials);
        return smr;
    }

    /**
     *
     * @param x
     * @param y
     * @param toCellCenter if true the center of the given point will always be
     * in the center of a cell. otherwise it will in the edge point of 4 cells.
     * @param dx
     * @param dy
     * @param numberXIntervals
     * @param numberYIntervals
     * @param numberMaterials
     * @param timec
     * @return
     */
    public static SurfaceMeasurementRaster RasterFocusOnPoint(double x, double y, boolean toCellCenter, double dx, double dy, int numberXIntervals, int numberYIntervals, int numberMaterials, TimeIndexContainer timec) {

        double xmin, ymin;
        if (toCellCenter) {
            //place focus point in the center of a raster cell
            xmin = x - dx * 0.5 - dx * (int) (numberXIntervals / 2);
            ymin = y - dy * 0.5 - dy * (int) (numberYIntervals / 2);
        } else {
            xmin = x - dx * (int) (numberXIntervals / 2);
            ymin = y - dy * (int) (numberYIntervals / 2);
        }

        SurfaceMeasurementRectangleRaster smr = new SurfaceMeasurementRectangleRaster(xmin, ymin, numberXIntervals, numberYIntervals, dx, dy, numberMaterials, timec);

        return smr;
    }

    @Override
    public void measureParticle(long time, Particle particle, int index) {
        if (particle.getPosition3d() == null) {
            return;
        }
        if (particle.getTravelledPathLength() < minTravelLengthToMeasure) {
            return;
        }
        if (this.times == null) {
            throw new NullPointerException("TimeContainer in " + getClass() + " not set.");
        }
        int timeIndex = this.times.getTimeIndex(time);
        int xindex = (int) ((particle.getPosition3d().x - xmin) / xIntervalWidth);
        int yindex = (int) ((particle.getPosition3d().y - ymin) / YIntervalHeight);

        if (xindex < 0 || yindex < 0 || xindex >= mass.length) {
            return;
        }
        if (yindex >= numberYIntervals) {
            return;
        }
        try {
//create counters if non existing
            if (mass[xindex] == null) {
                synchronized (mass) {
                    if (mass[xindex] == null) {
                        mass[xindex] = new double[numberYIntervals][][];
                        particlecounter[xindex] = new int[numberYIntervals][][];
                    }
                }
            }
            if (mass[xindex][yindex] == null) {
                synchronized (mass) {
                    if (mass[xindex][yindex] == null) {
                        mass[xindex][yindex] = new double[times.getNumberOfTimes()][numberOfMaterials];
                        particlecounter[xindex][yindex] = new int[times.getNumberOfTimes()][numberOfMaterials];
                    }
                }
            }
//count particle
            if (synchronizeMeasures) {
                synchronized (mass[xindex][yindex][timeIndex]) {
                    mass[xindex][yindex][timeIndex][particle.getMaterial().materialIndex] += particle.getParticleMass();
                    particlecounter[xindex][yindex][timeIndex][particle.getMaterial().materialIndex]++;
                }
            } else {
                mass[xindex][yindex][timeIndex][particle.getMaterial().materialIndex] += particle.getParticleMass();
                particlecounter[xindex][yindex][timeIndex][particle.getMaterial().materialIndex]++;
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("X index: " + xindex + "    pos.x=" + particle.getPosition3d().x + "    xmin=" + xmin + "     diff=" + ((particle.getPosition3d().x - xmin) + "    xIwidth=" + xIntervalWidth));
        }
    }

    @Override
    public void setNumberOfMaterials(int numberOfMaterials
    ) {
        if (this.numberOfMaterials == numberOfMaterials) {
            return;
        }
        this.numberOfMaterials = numberOfMaterials;
    }

    @Override
    public void setTimeContainer(TimeIndexContainer times
    ) {
        this.times = times;
    }

    public int getNumberOfMaterials() {
        return numberOfMaterials;
    }

    public double getXmin() {
        return xmin;
    }

    public double getYmin() {
        return ymin;
    }

    public double getYIntervalHeight() {
        return YIntervalHeight;
    }

    public double getxIntervalWidth() {
        return xIntervalWidth;
    }

    public int getNumberXIntervals() {
        return numberXIntervals;
    }

    public int getNumberYIntervals() {
        return numberYIntervals;
    }

    /**
     * [x-index][y-index][timeindex][materialindex]:counter
     *
     * @return number of particles in raster
     */
    public int[][][][] getParticlecounter() {
        return particlecounter;
    }

    public int getParticlesCounted(int xindex, int yindex, int timeindex, int materialindex) {
        if (particlecounter == null) {
            return 0;
        }
        if (particlecounter[xindex] == null) {
            return 0;
        }
        if (particlecounter[xindex][yindex] == null) {
            return 0;
        }
        if (particlecounter[xindex][yindex][timeindex] == null) {
            return 0;
        }
        return particlecounter[xindex][yindex][timeindex][materialindex];

    }

    public int getParticlesCountedMaterialSum(int xindex, int yindex, int timeindex) {
        if (particlecounter == null) {
            return 0;
        }
        if (particlecounter[xindex] == null) {
            return 0;
        }
        if (particlecounter[xindex][yindex] == null) {
            return 0;
        }
        if (particlecounter[xindex][yindex][timeindex] == null) {
            return 0;
        }
        int sum = 0;
        for (int i = 0; i < numberOfMaterials; i++) {
            sum += particlecounter[xindex][yindex][timeindex][i];
        }
        return sum;
    }

    public int getMaxParticleCount() {
        int maxSum = 0;
        for (int x = 0; x < numberXIntervals; x++) {
            if (particlecounter[x] == null) {
                continue;
            }
            for (int y = 0; y < numberYIntervals; y++) {
                if (particlecounter[x][y] == null) {
                    continue;
                }
                for (int t = 0; t < times.getNumberOfTimes(); t++) {
                    int sum = 0;
                    for (int m = 0; m < numberOfMaterials; m++) {
                        sum += particlecounter[x][y][t][m];
                    }
                    maxSum = Math.max(maxSum, sum);
                }
            }
        }
        return maxSum;
    }

    public Coordinate getMidCoordinate(int xindex, int yindex) {
        return new Coordinate(xmin + (xindex + 0.5) * xIntervalWidth, ymin + (yindex + 0.5) * YIntervalHeight);
    }

    /**
     * Returns 4 coordinates
     *
     * @param xindex
     * @param yindex
     * @return
     */
    public Coordinate[] getRectangleBound(int xindex, int yindex) {
        Coordinate[] cs = new Coordinate[4];
        cs[0] = new Coordinate(xmin + (xindex) * xIntervalWidth, ymin + (yindex) * YIntervalHeight);
        cs[2] = new Coordinate(xmin + (xindex + 1) * xIntervalWidth, ymin + (yindex + 1) * YIntervalHeight);
        cs[1] = new Coordinate(cs[2].x, cs[0].y);
        cs[3] = new Coordinate(cs[0].x, cs[2].y);

        return cs;
    }

    /**
     * Returns 5 coordinates
     *
     * @param xindex
     * @param yindex
     * @return
     */
    public Coordinate[] getRectangleBoundClosed(int xindex, int yindex) {
        Coordinate[] cs = new Coordinate[5];
        cs[0] = new Coordinate(xmin + (xindex) * xIntervalWidth, ymin + (yindex) * YIntervalHeight);
        cs[2] = new Coordinate(xmin + (xindex + 1) * xIntervalWidth, ymin + (yindex + 1) * YIntervalHeight);
        cs[1] = new Coordinate(cs[2].x, cs[0].y);
        cs[3] = new Coordinate(cs[0].x, cs[2].y);
        cs[4] = cs[0];
        return cs;
    }

    public int getNumberOfTimes() {
        if (this.times == null) {
            return 0;
        }
        return this.times.getNumberOfTimes();
    }

    @Override
    public void reset() {
        mass = new double[numberXIntervals][][][];
        particlecounter = new int[numberXIntervals][][][];
    }

    @Override
    public void breakAllLocks() {
        throw new UnsupportedOperationException("RectangularRaster is working with 'synchronize' that cannot be unlocked"); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void synchronizeMeasurements() {
        return; //Not needed
    }
}
